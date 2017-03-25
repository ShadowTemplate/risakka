package risakka.raft.actor;

import akka.actor.ActorSelection;
import akka.actor.Cancellable;
import akka.persistence.*;
import akka.routing.Router;

import java.util.*;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.Setter;
import risakka.gui.EventNotifier;
import risakka.raft.log.LogEntry;
import risakka.raft.log.StateMachineCommand;
import risakka.raft.message.MessageToServer;
import risakka.raft.message.akka.ClusterConfigurationMessage;
import risakka.raft.message.akka.ElectionTimeoutMessage;
import risakka.raft.message.akka.SendHeartbeatMessage;
import risakka.raft.message.rpc.client.RegisterClientRequest;
import risakka.raft.message.rpc.client.RegisterClientResponse;
import risakka.raft.message.rpc.client.ServerResponse;
import risakka.raft.message.rpc.client.Status;
import risakka.raft.message.rpc.server.AppendEntriesRequest;
import risakka.raft.message.rpc.server.RequestVoteRequest;
import risakka.raft.miscellanea.LRUSessionMap;
import risakka.raft.miscellanea.PersistentState;
import risakka.raft.miscellanea.ServerState;
import risakka.util.Conf;
import risakka.util.Util;
import scala.concurrent.duration.Duration;

@Getter
@Setter
public class RaftServer extends UntypedPersistentActor {

    // Raft paper fields
    private PersistentState persistentState;

    // volatile fields
    private Integer commitIndex;
    private Integer lastApplied;

    // leader volatile fields // TODO REINITIALIZE AFTER ELECTION
    private int[] nextIndex;
    private int[] matchIndex;


    // Raft other fields

    // volatile TODO is this right?
    private ServerState state; // FOLLOWER / CANDIDATE / LEADER
    private Set<String> votersIds;
    private Integer leaderId; //last leader known
    private LRUSessionMap<Integer, Integer> clientSessionMap;

    // Akka fields

    // volatile fields
    private Router broadcastRouter;
    private Cancellable heartbeatSchedule;
    private Cancellable electionSchedule;

    private EventNotifier eventNotifier;
    private Integer id;


    public RaftServer(Integer id) {
        System.out.println("Creating RaftServer with id " + id);
        this.votersIds = new HashSet<>();
        this.clientSessionMap = new LRUSessionMap<>(Conf.MAX_CLIENT_SESSIONS);
        this.persistentState = new PersistentState();
        this.nextIndex = new int[Conf.SERVER_NUMBER];
        this.matchIndex = new int[Conf.SERVER_NUMBER];
        this.initializeNextAndMatchIndex();
        this.commitIndex = 0;
        this.lastApplied = 0;
        this.leaderId = null;
        this.broadcastRouter = null;
        this.eventNotifier = null;
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
        System.out.println(getSelf().path().name() + " has received command " + message.getClass().getSimpleName());

        if (persistentState.getActorsRefs() == null && broadcastRouter == null && eventNotifier == null // server not initialized
                && message instanceof MessageToServer // not an Akka internal message (e.g. snapshot-related) I would still be able to process
                && !(message instanceof ClusterConfigurationMessage)) { // not the message I was waiting to init myself
            System.out.println(getSelf().path().name() + " can't process message because it is still uninitialized");
            unhandled(message);
            return;
        }

        if (message instanceof MessageToServer) {
            ((MessageToServer) message).onReceivedBy(this);
        } else if (message instanceof SaveSnapshotSuccess) {
            if (eventNotifier != null) {
                eventNotifier.addMessage(id, "[IN] " + message.getClass().getSimpleName());
            }
        } else if (message instanceof SaveSnapshotFailure) {
            System.out.println("Error while performing the snapshot. " + message);
            if (eventNotifier != null) {
                eventNotifier.addMessage(id, "[IN] " + message.getClass().getSimpleName() + "\nCause: " + ((SaveSnapshotFailure) message).cause());
            }
        } else {
            System.out.println("Unknown message type: " + message.getClass());
            if (eventNotifier != null) {
                eventNotifier.addMessage(id, "[IN] Unknown message type: " + message.getClass().getSimpleName());
            }
            unhandled(message);
        }
    }


    @Override
    public void onReceiveRecover(Object message) throws Throwable {
        System.out.println(getSelf().path().name() + " has received recover " + message.getClass().getSimpleName());
        if (message instanceof SnapshotOffer) { // called when server recovers from durable storage
            persistentState = buildFromSnapshotOffer((SnapshotOffer) message);
            System.out.println(getSelf().path().name() + " has loaded old " + persistentState.getClass().getSimpleName());
        } else if (message instanceof RecoveryCompleted) {
            System.out.println("Recovery completed");
            System.out.println(persistentState.toString());
            //actor can do something else before processing any other message
        } else {
            System.out.println(getSelf().path().name() + " is unable to process "
                    + message.getClass().getSimpleName() + ". Forwarding to onReceiveCommand()...");
            onReceiveCommand(message);
        }
    }

    public void toFollowerState() {
        state = ServerState.FOLLOWER;
        if (eventNotifier != null) {
            eventNotifier.updateState(id, state);
        }
        System.out.println(getSelf().path().name() + ": toFollowerState called");
        cancelSchedule(heartbeatSchedule); // Required when state changed from LEADER to FOLLOWER
        scheduleElection();
    }

    public void toCandidateState() {
        state = ServerState.CANDIDATE; // c
        if (eventNotifier != null) {
            eventNotifier.updateState(id, state);
        }
        onConversionToCandidate(); // e
    }

    public void toLeaderState() {
        state = ServerState.LEADER;
        if (eventNotifier != null) {
            eventNotifier.updateState(id, state);
        }
        leaderId = id;
        cancelSchedule(electionSchedule);
        startHeartbeating();
        // Reinitialize volatile state after election
        initializeNextAndMatchIndex(); //B
        sendNoOp(); // used by the current leader to ensure that current term entry are stored on a majority of servers
    }

    private void sendNoOp() {
        StateMachineCommand nop = new StateMachineCommand("NO-OP", getSelf());

        addEntryToLogAndSendToFollowers(nop);
    }

    private void startHeartbeating() {
        cancelSchedule(heartbeatSchedule);
        // Schedule a new heartbeat for itself. Starts immediately and repeats every HEARTBEAT_FREQUENCY
        heartbeatSchedule = getContext().system().scheduler().schedule(
                Duration.Zero(), // q
                // Duration.create(0, TimeUnit.MILLISECONDS), // q
                Duration.create(Conf.HEARTBEAT_FREQUENCY, TimeUnit.MILLISECONDS), getSelf(), new SendHeartbeatMessage(), // q
                getContext().system().dispatcher(), getSelf());
    }

    public void scheduleElection() {
        // TODO check if, in addition, ElectionTimeoutMessage in Inbox should be removed
        cancelSchedule(electionSchedule);
        // Schedule a new election for itself. Starts after ELECTION_TIMEOUT
        int electionTimeout = Util.getElectionTimeout(); // p
        System.out.println(getSelf().path().name() + " election timeout: " + electionTimeout);
        electionSchedule = getContext().system().scheduler().scheduleOnce(
                Duration.create(electionTimeout, TimeUnit.MILLISECONDS), getSelf(), new ElectionTimeoutMessage(),
                getContext().system().dispatcher(), getSelf());
    }

    private void cancelSchedule(Cancellable schedule) {
        if (schedule != null && !schedule.isCancelled()) {
            schedule.cancel();
        }
    }

    private void onConversionToCandidate() {  // e
        beginElection(); // d
    }

    public void beginElection() { // d
        votersIds.clear();
        persistentState.updateCurrentTerm(this, persistentState.getCurrentTerm() + 1); // b
        if (eventNotifier != null) {
            eventNotifier.updateTerm(id, persistentState.getCurrentTerm());
        }
        leaderId = null;
        getPersistentState().updateVotedFor(this, getSelf());
        votersIds.add(getSelf().path().toSerializationFormat()); // f
        // TODO change randomly my electionTimeout
        scheduleElection(); // g
        System.out.println(getSelf().path().name() + " will broadcast RequestVoteRequest");

        int lastLogIndex = persistentState.getLog().size();
        int lastLogTerm = getLastLogTerm(lastLogIndex);
        broadcastRouter.route(new RequestVoteRequest(persistentState.getCurrentTerm(), lastLogIndex, lastLogTerm), getSelf());
    }

    // TODO move the following methods in an appropriate location

    public void initializeNextAndMatchIndex() { //B
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
        return getContext().actorSelection("akka.tcp://" + Conf.CLUSTER_NAME + "@" + Conf.NODES_IPS[id] + ":"
                + Conf.NODES_PORTS[id] + "/user/node_" + id);
    }

    public void addEntryToLogAndSendToFollowers(StateMachineCommand command) { //u
        LogEntry entry = new LogEntry(persistentState.getCurrentTerm(), command);
        int lastIndex = persistentState.getLog().size();
        persistentState.updateLog(this, lastIndex + 1, entry);
        eventNotifier.updateLog(id, lastIndex + 1, entry);

        sendAppendEntriesToAllFollowers(); //w
    }

    public void sendAppendEntriesToAllFollowers() { //w
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

                actor.tell(new AppendEntriesRequest(server.getPersistentState().getCurrentTerm(), nextIndex[followerId] - 1, prevLogTerm, entries, server.getCommitIndex()), getSelf());
            } else { //first entry - previous entry fields are null
                actor.tell(new AppendEntriesRequest(server.getPersistentState().getCurrentTerm(), null, null, entries, server.getCommitIndex()), getSelf());
            }
        }
    }

    public void checkEntriesToCommit() { // z //call iff leader
        System.out.println("Checking if some entries can be committed");
        int oldCommitIndex = commitIndex;
        for (int i = persistentState.getLog().size(); i > commitIndex; i--) {
            int count = 1; // on how many server the entry is replicated (myself for sure)

            for (Integer index : matchIndex) {
                if (index >= i && persistentState.getLog().get(i).getTermNumber().equals(persistentState.getCurrentTerm())) {
                    count++;
                }
            }

            if (count > Conf.SERVER_NUMBER / 2) {
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

        if (command.getCommand().equals("NOP") && !leader) {
            System.out.println("[DEBUG- TEST] Received a NOP. Operation not executed");
            return;
        }

        //registration command of new client - new client session
        if (command.getCommand().equals(RegisterClientRequest.REGISTER)) {
            System.out.println("Registering client " + command.getClientAddress());
            clientSessionMap.put(logIndex, -1); //allocate new session
            if (leader) { //answer back to the client
                command.getClientAddress().tell(new RegisterClientResponse(Status.OK, logIndex, null), getSelf());
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

            System.out.println("committing request: " + command.getCommand() + " of client " + clientSessionMap.get(command.getClientId()));
            //update last request of the client
            clientSessionMap.put(command.getClientId(), Math.max(command.getSeqNumber(), lastSeqNumber));
            
            if (leader) { //answer back to the client
                command.getClientAddress().tell(new ServerResponse(Status.OK, result, null), getSelf());
            }
            //TODO update lastApplied
            return;
        } 

        //client session is expired, command should not be executed
        System.out.println("Client session of " + command.getClientId() + " is expired");
        if (leader) {
            command.getClientAddress().tell(new ServerResponse(Status.SESSION_EXPIRED, null, null), getSelf());
        }
        //TODO update lastApplied?
    }
    
    private String applyToStateMachine(int index) {
        String result = "(log " + index + ") " + Long.toHexString(Double.doubleToLongBits(Math.random()));
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

    private PersistentState buildFromSnapshotOffer(SnapshotOffer snapshotOffer) {
        PersistentState loadedState = (PersistentState) snapshotOffer.snapshot();
        loadedState.recreateBroadcastRouter(this);
        return loadedState;
    }

}
