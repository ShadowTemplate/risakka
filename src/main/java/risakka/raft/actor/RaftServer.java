package risakka.raft.actor;

import risakka.util.conf.server.ServerConfImpl;
import risakka.util.conf.server.ServerConf;
import akka.actor.ActorSelection;
import akka.actor.Cancellable;
import akka.persistence.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.Setter;
import risakka.gui.EventNotifier;
import risakka.raft.log.LogEntry;
import risakka.raft.log.StateMachineCommand;
import risakka.raft.message.MessageToServer;
import risakka.raft.message.akka.*;
import risakka.raft.message.rpc.client.*;
import risakka.raft.message.rpc.server.*;
import risakka.raft.miscellanea.*;
import risakka.util.*;
import scala.concurrent.duration.Duration;

@Getter
@Setter
public class RaftServer extends UntypedPersistentActor {

    // Raft paper fields
    private PersistentState persistentState;

    // volatile fields
    private Integer commitIndex;
    private Integer lastApplied;

    // leader volatile fields
    private int[] nextIndex;
    private int[] matchIndex;


    // Raft other fields

    // volatile
    private ServerState state; // FOLLOWER / CANDIDATE / LEADER
    private Set<String> votersIds;
    private Integer leaderId; //last leader known
    private LRUSessionMap<Integer, Integer> clientSessionMap;

    // Akka fields

    // volatile fields
    private Cancellable heartbeatSchedule;
    private Cancellable electionSchedule;

    private Integer id;
    private final ServerConfImpl serverConf;

    public RaftServer(Integer id) {
        System.out.println("Creating RaftServer with id " + id);
        serverConf = ServerConf.SettingsProvider.get(getContext().system());
        this.votersIds = new HashSet<>();
        this.clientSessionMap = new LRUSessionMap<>(serverConf.MAX_CLIENT_SESSIONS);
        this.persistentState = new PersistentState();
        this.nextIndex = new int[serverConf.SERVER_NUMBER];
        this.matchIndex = new int[serverConf.SERVER_NUMBER];
        this.initializeNextAndMatchIndex();
        this.commitIndex = 0;
        this.lastApplied = 0;
        this.leaderId = null;
        this.id = id;
    }

    @Override
    public void preStart() throws Exception {
        toFollowerState();
        Recovery.create();
    }

    // TODO Promemoria: rischedulare immediatamente HeartbeatTimeout appena si ricevono notizie dal server.

    @Override
    public void onReceiveCommand(Object message) throws Throwable {
        System.out.println("[" + getSelf().path().name() + "] Received command " + message.getClass().getSimpleName());

        if (persistentState.getActorsRefs() == null && EventNotifier.getInstance() == null // server not initialized
                && message instanceof MessageToServer // not an Akka internal message (e.g. snapshot-related) I would still be able to process
                && !(message instanceof ClusterConfigurationMessage)) { // not the message I was waiting to init myself
            System.out.println("[" + getSelf().path().name() + "] can't process message because it is still uninitialized");
            unhandled(message);
            return;
        }

        if (message instanceof MessageToServer) {
            ((MessageToServer) message).onReceivedBy(this);
        } else if (message instanceof SaveSnapshotSuccess) {
            if (EventNotifier.getInstance() != null) {
                EventNotifier.getInstance().addMessage(id, "[IN] " + message.getClass().getSimpleName());
            }
        } else if (message instanceof SaveSnapshotFailure) {
            System.out.println("[" + getSelf().path().name() + "] Error while performing the snapshot. " + message);
            if (EventNotifier.getInstance() != null) {
                EventNotifier.getInstance().addMessage(id, "[IN] " + message.getClass().getSimpleName() + "\nCause: " + ((SaveSnapshotFailure) message).cause());
            }
        } else {
            System.out.println("[" + getSelf().path().name() + "] Unknown message type: " + message.getClass());
            if (EventNotifier.getInstance() != null) {
                EventNotifier.getInstance().addMessage(id, "[IN] Unknown message type: " + message.getClass().getSimpleName());
            }
            unhandled(message);
        }
    }

    @Override
    public void onReceiveRecover(Object message) throws Throwable {
        System.out.println("[" + getSelf().path().name() + "] Received recover " + message.getClass().getSimpleName());
        if (message instanceof PersistentState) { // called when server recovers from durable storage
            persistentState = (PersistentState) message; //buildFromSnapshotOffer((PersistentState) message);
            System.out.println(getSelf().path().name() + " has loaded old " + persistentState.getClass().getSimpleName());
        } else if (message instanceof RecoveryCompleted) {
            System.out.println("[" + getSelf().path().name() + "] Recovery completed: " + persistentState.toString());
            //actor can do something else before processing any other message
        } else {
            System.out.println("[" + getSelf().path().name() + "] Unable to process "
                    + message.getClass().getSimpleName() + ". Forwarding to onReceiveCommand()...");
            onReceiveCommand(message);
        }
    }

    public void toFollowerState() {
        System.out.println("[" + getSelf().path().name() + "] toFollowerState");
        state = ServerState.FOLLOWER;
        if (EventNotifier.getInstance() != null) {
            EventNotifier.getInstance().updateState(id, state);
        }
        cancelSchedule(heartbeatSchedule); // Required when state changed from LEADER to FOLLOWER
        scheduleElection();
    }

    public void toCandidateState() {
        System.out.println("[" + getSelf().path().name() + "] toCandidateState");
        state = ServerState.CANDIDATE; // c
        if (EventNotifier.getInstance() != null) {
            EventNotifier.getInstance().updateState(id, state);
        }
        beginElection(); // e, d
    }

    public void toLeaderState() {
        System.out.println("[" + getSelf().path().name() + "] toLeaderState");
        state = ServerState.LEADER;
        if (EventNotifier.getInstance() != null) {
            EventNotifier.getInstance().updateState(id, state);
        }
        leaderId = id;
        cancelSchedule(electionSchedule);
        startHeartbeating();
        // Reinitialize volatile state after election
        initializeNextAndMatchIndex(); //B
        sendNoOp(); // used by the current leader to ensure that current term entry are stored on a majority of servers
    }

    private void sendNoOp() {
        StateMachineCommand nop = new StateMachineCommand(StateMachineCommand.NOOP, getSelf());
        addEntryToLogAndSendToFollowers(nop);
    }

    private void startHeartbeating() {
        cancelSchedule(heartbeatSchedule);
        // Schedule a new heartbeat for itself. Starts immediately and repeats every HEARTBEAT_FREQUENCY
        heartbeatSchedule = getContext().system().scheduler().schedule(Duration.Zero(), // q
                // Duration.create(0, TimeUnit.MILLISECONDS), // q
Duration.create(serverConf.HEARTBEAT_FREQUENCY, TimeUnit.MILLISECONDS), getSelf(), new SendHeartbeatMessage(), // q
                getContext().system().dispatcher(), getSelf());
    }

    public void scheduleElection() {
        // TODO check if, in addition, ElectionTimeoutMessage in Inbox should be removed
        cancelSchedule(electionSchedule);
        // Schedule a new election for itself. Starts after ELECTION_TIMEOUT
        int electionTimeout = Util.getRandomElectionTimeout(serverConf.HEARTBEAT_FREQUENCY); // p
        System.out.println(getSelf().path().name() + " election timeout: " + electionTimeout);
        System.out.println("[" + getSelf().path().name() + "] New election timeout: " + electionTimeout);
        electionSchedule = getContext().system().scheduler().scheduleOnce(
                Duration.create(electionTimeout, TimeUnit.MILLISECONDS), getSelf(), new ElectionTimeoutMessage(),
                getContext().system().dispatcher(), getSelf());
    }

    private void cancelSchedule(Cancellable schedule) {
        if (schedule != null && !schedule.isCancelled()) {
            schedule.cancel();
        }
    }

    public void beginElection() { // d
        System.out.println("[" + getSelf().path().name() + "] Starting election");
        votersIds.clear();
        persistentState.updateCurrentTerm(this, persistentState.getCurrentTerm() + 1, () -> { // b
            System.out.println("Updating term");
            if (EventNotifier.getInstance() != null) {
                EventNotifier.getInstance().updateTerm(id, persistentState.getCurrentTerm());
            }
            leaderId = null;
            getPersistentState().updateVotedFor(this, getSelf(), () -> {
                votersIds.add(getSelf().path().toSerializationFormat()); // f
                scheduleElection(); // g
                System.out.println("Scheduling election");
                int lastLogIndex = persistentState.getLog().size();
                int lastLogTerm = getLastLogTerm(lastLogIndex);
                sendBroadcastRequest(new RequestVoteRequest(persistentState.getCurrentTerm(), lastLogIndex, lastLogTerm));
            });
        });
        System.out.println("fuori");
    }

    private void sendBroadcastRequest(MessageToServer message) {
        System.out.println("[" + getSelf().path().name() + "] [OUT Broadcast] " + message.getClass().getSimpleName());
        EventNotifier.getInstance().addMessage(id, "[OUT Broadcast] " + message.getClass().getSimpleName());
        persistentState.getActorsRefs().stream().filter(actorRef -> !actorRef.equals(getSelf())).forEach(actorRef -> {
            actorRef.tell(message, getSelf());
        });
    }

    private void initializeNextAndMatchIndex() { //B
        for (int i = 0; i < nextIndex.length; i++) {
            nextIndex[i] = persistentState.getLog().size() + 1;
            matchIndex[i] = 0;
        }
    }

    public int getLastLogTerm(int lastLogIndex) {
        return lastLogIndex <= 0 ? 0 : persistentState.getLog().get(lastLogIndex).getTermNumber();
    }

    public void updateNextIndexAtIndex(int index, int value) {
        nextIndex[index] = value;
    }

    public void updateMatchIndexAtIndex(int index, int value) {
        matchIndex[index] = value;
    }

    private ActorSelection buildAddressFromId(int id) {
        return getContext().actorSelection("akka.tcp://" + serverConf.CLUSTER_NAME + "@" + serverConf.NODES_IPS[id] + ":"
                + serverConf.NODES_PORTS[id] + "/user/" + serverConf.PREFIX_NODE_NAME + id);
    }

    public void addEntryToLogAndSendToFollowers(StateMachineCommand command) { //u
        LogEntry entry = new LogEntry(persistentState.getCurrentTerm(), command);
        int lastIndex = persistentState.getLog().size();
        persistentState.updateLog(this, lastIndex + 1, entry, () -> {
            System.out.println("[" + getSelf().path().name() + "] Updated log: [" + lastIndex + 1 + "] " + entry);
            EventNotifier.getInstance().updateLog(id, lastIndex + 1, entry);
            sendAppendEntriesToAllFollowers(); //w
        });
    }

    private void sendAppendEntriesToAllFollowers() { //w
        for (int i = 0; i < nextIndex.length; i++) {
            if (i != id) { //if not myself
                sendAppendEntriesToOneFollower(this, i);
            }
        }
    }

    public void sendAppendEntriesToOneFollower(RaftServer server, Integer followerId) { //w
        int lastIndex = server.getPersistentState().getLog().size();
        if (lastIndex >= nextIndex[followerId]) {

            ActorSelection actor = buildAddressFromId(followerId);
            //get new entries
            List<LogEntry> entries = new ArrayList<>();
            for (int j = nextIndex[followerId]; j <= lastIndex; j++) {
                entries.add(server.getPersistentState().getLog().get(j)); // TODO fix bug here
            }

            if (nextIndex[followerId] > 1) { //at least one entry is already committed
                //previous entry w.r.t. the new ones that has to match in order to accept the new ones
                LogEntry prevEntry = server.getPersistentState().getLog().get(nextIndex[followerId] - 1);
                Integer prevLogTerm = prevEntry.getTermNumber();
                AppendEntriesRequest appendEntriesRequest = new AppendEntriesRequest(server.getPersistentState().getCurrentTerm(), nextIndex[followerId] - 1, prevLogTerm, entries, server.getCommitIndex());
                System.out.println("[" + getSelf().path().name() + "] [OUT] AppendEntriesRequest " + appendEntriesRequest);
                EventNotifier.getInstance().addMessage(id, "[OUT] AppendEntriesRequest " + appendEntriesRequest);
                actor.tell(appendEntriesRequest, getSelf());
            } else { //first entry - previous entry fields are null
                AppendEntriesRequest appendEntriesRequest = new AppendEntriesRequest(server.getPersistentState().getCurrentTerm(), null, null, entries, server.getCommitIndex());
                System.out.println("[" + getSelf().path().name() + "] [OUT] AppendEntriesRequest " + appendEntriesRequest);
                EventNotifier.getInstance().addMessage(id, "[OUT] AppendEntriesRequest " + appendEntriesRequest);
                actor.tell(appendEntriesRequest, getSelf());
            }
        }
    }

    public void checkEntriesToCommit() { // z //call iff leader
        System.out.println("[" + getSelf().path().name() + "] Checking if some entries can be committed");
        int oldCommitIndex = commitIndex;
        for (int i = persistentState.getLog().size(); i > commitIndex; i--) {
            int count = 1; // on how many server the entry is replicated (myself for sure)

            for (Integer index : matchIndex) {
                if (index >= i && persistentState.getLog().get(i).getTermNumber().equals(persistentState.getCurrentTerm())) {
                    count++;
                }
            }

            if (count > serverConf.SERVER_NUMBER / 2) {
                commitIndex = i;
                executeCommands(oldCommitIndex + 1, commitIndex, true);
                break;
            }
        }
    }

    public void executeCommands(int minIndex, int maxIndex, Boolean leader) {
        for (int i = minIndex; i <= maxIndex; i++) { //v send answer back to the client when committed
            executeCommand(i, leader);
        }
    }
    
    private void executeCommand(int logIndex, Boolean leader) {
        StateMachineCommand command = persistentState.getLog().get(logIndex).getCommand();

        if (command.getCommand().equals(StateMachineCommand.NOOP)) {
            System.out.println("[" + getSelf().path().name() + "] Received a NO-OP: operation not executed");
            return;
        }

        //registration command of new client - new client session
        if (command.getCommand().equals(RegisterClientRequest.REGISTER)) {
            System.out.println("[" + getSelf().path().name() + "] Registering client " + command.getClientAddress());
            EventNotifier.getInstance().addMessage(id, "Registering client " + command.getClientAddress());
            clientSessionMap.put(logIndex, -1); //allocate new session
            if (leader) { //answer back to the client
                RegisterClientResponse registerClientResponse = new RegisterClientResponse(Status.OK, logIndex, null);
                System.out.println("[" + getSelf().path().name() + "] [OUT] " + RegisterClientResponse.class.getSimpleName()
                        + " " + registerClientResponse);
                EventNotifier.getInstance().addMessage(id, "[OUT] " + RegisterClientResponse.class.getSimpleName() + " " + registerClientResponse);
                command.getClientAddress().tell(registerClientResponse, getSelf());
            }
            //TODO update lastApplied
            return;
        }

        //command of client with a valid session
        if (clientSessionMap.containsKey(command.getClientId())) {
            int lastSeqNumber = clientSessionMap.get(command.getClientId());

            String result = "";
            if (command.getSeqNumber() > lastSeqNumber) { //first time
                //execute command on state machine
                result = applyToStateMachine(logIndex);
                
            } else { //duplicate request - not execute again
                if (leader) {  //retrieve response to send to client in the log
                    for (int i = logIndex - 1; i >= 0; i--) {
                        if (persistentState.getLog().get(i).getCommand().getClientId().equals(command.getClientId())
                                && persistentState.getLog().get(i).getCommand().getSeqNumber().equals(command.getSeqNumber())) {
                            //retrieve result
                            result = persistentState.getLog().get(i).getCommand().getResult();
                            command.setResult(result); //set same result also to the duplicate entry
                            break;
                        }
                    }
                }
            }

            //update last request of the client
            clientSessionMap.put(command.getClientId(), Math.max(command.getSeqNumber(), lastSeqNumber));
            System.out.println("[" + getSelf().path().name() + "] Committing request: " + command.getCommand() +
                    " of client " + command.getClientId() + " and seqNumber: " + clientSessionMap.get(command.getClientId()));
            EventNotifier.getInstance().addMessage(id, "Committing request: " + command.getCommand() +
                    " of client " + command.getClientId() + " and seqNumber: " + clientSessionMap.get(command.getClientId()));
            
            if (leader) { //answer back to the client
                ServerResponse serverResponse = new ServerResponse(Status.OK, result, null);
                System.out.println("[" + getSelf().path().name() + "] [OUT] " + ServerResponse.class.getSimpleName()
                        + serverResponse);
                EventNotifier.getInstance().addMessage(id, "[OUT] " + ServerResponse.class.getSimpleName() + serverResponse);
                command.getClientAddress().tell(serverResponse, getSelf());
            }
            //TODO update lastApplied
            return;
        } 

        //client session is expired, command should not be executed
        System.out.println("[" + getSelf().path().name() + "] Client session of " + command.getClientId() + " is expired");
        EventNotifier.getInstance().addMessage(id, "Client session of " + command.getClientId() + " is expired");
        if (leader) {
            ServerResponse serverResponse = new ServerResponse(Status.SESSION_EXPIRED, null, null);
            System.out.println("[" + getSelf().path().name() + "] [OUT] " + ServerResponse.class.getSimpleName() + serverResponse);
            EventNotifier.getInstance().addMessage(id, "[OUT] " + ServerResponse.class.getSimpleName() + serverResponse);
            command.getClientAddress().tell(serverResponse, getSelf());
        }
        //TODO update lastApplied?
    }
    
    private String applyToStateMachine(int index) {
        String result = "(log " + index + ") " + Long.toHexString(Double.doubleToLongBits(Math.random()));
        System.out.println("[" + getSelf().path().name() + "] Applying to state machine. [" + index + "] " + result);
        EventNotifier.getInstance().addMessage(id, "Applying to state machine. [" + index + "] " + result);
        persistentState.getLog().get(index).getCommand().setResult(result);
        return result;
    }

    public int getSenderServerId() {
        String serverName = getSender().path().name(); //e.g. node_0
        return Character.getNumericValue(serverName.charAt(serverName.length() - 1));
    }

    @Override
    public String persistenceId() {
        return "id_"; // TODO check
    }

    //Still here because it could be useful if we decide to use a snapshot to delete old journals
    private PersistentState buildFromSnapshotOffer(SnapshotOffer snapshotOffer) {
        return (PersistentState) snapshotOffer.snapshot();
    }
}
