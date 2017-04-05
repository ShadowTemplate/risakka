package risakka.raft.actor;

import akka.japi.Procedure;
import org.apache.log4j.Logger;
import risakka.util.conf.server.ServerConfImpl;
import risakka.util.conf.server.ServerConf;
import akka.actor.ActorSelection;
import akka.actor.Cancellable;
import akka.persistence.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.Setter;
import risakka.raft.miscellanea.EventNotifier;
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

    private static final Logger logger = Logger.getLogger(RaftServer.class);

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
    private List<String> actorAddresses;

    // Akka fields

    // volatile fields
    private Cancellable heartbeatSchedule;
    private Cancellable electionSchedule;

    private Integer id;
    private final ServerConfImpl serverConf;

    public RaftServer(Integer id) {
        logger.info("QUESTA E' UNA INFO DEL LOGGER");
        logger.debug("QUESTO E' UN WARN");
        logger.info("Creating RaftServer with id " + id);
        serverConf = ServerConf.SettingsProvider.get(getContext().system());
        this.id = id;
        this.votersIds = new HashSet<>();
        this.clientSessionMap = new LRUSessionMap<>(serverConf.MAX_CLIENT_SESSIONS);
        this.persistentState = new PersistentState();
        this.nextIndex = new int[serverConf.SERVER_NUMBER];
        this.matchIndex = new int[serverConf.SERVER_NUMBER];
        this.initializeNextAndMatchIndex();
        this.initializeActorAddresses();
        this.commitIndex = 0;
        this.lastApplied = 0;
        this.leaderId = null;
    }

    @Override
    public void preStart() throws Exception {
        toFollowerState();
        Recovery.create();
    }

    private Procedure<Object> activeActor = message -> {
        if (!(message instanceof ResumeMessage)) { // discard ResumeMessage when actor is already active
            try {
                onReceiveCommand(message); // the behaviour when active is the same as usual
            } catch (Throwable throwable) {
                System.err.println(throwable.getMessage());
                throwable.printStackTrace();
            }
        }
    };

    private Procedure<Object> pausedActor = message -> {
        if (!(message instanceof PauseMessage)) { // discard PauseMessage when actor is already paused
            /*
            * The only messages to be processed when the actor is paused are ResumeMessages that will change the actor
            * back to the active state. All the other messages must be stashed. They will be processed as soon as the
            * actor becomes active again, in the same order.
            */
            if (message instanceof ResumeMessage) {
                ((ResumeMessage) message).onReceivedBy(this);
            } else {
                stash(); // do not process message and put it into the queue instead
            }
        }
    };

    @Override
    public void onReceiveCommand(Object message) throws Throwable {
        logger.debug("[" + getSelf().path().name() + "] Received command " + message.getClass().getSimpleName());

        if (actorAddresses == null && EventNotifier.getInstance() == null // server not initialized
                && message instanceof MessageToServer) { // not an Akka internal message (e.g. snapshot-related) I would still be able to process
            logger.debug("[" + getSelf().path().name() + "] can't process message because it is still uninitialized");
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
            logger.error("[" + getSelf().path().name() + "] Error while performing the snapshot. " + message);
            if (EventNotifier.getInstance() != null) {
                EventNotifier.getInstance().addMessage(id, "[IN] " + message.getClass().getSimpleName() + "\nCause: " + ((SaveSnapshotFailure) message).cause());
            }
        } else {
            logger.debug("[" + getSelf().path().name() + "] Unknown message type: " + message.getClass());
            if (EventNotifier.getInstance() != null) {
                EventNotifier.getInstance().addMessage(id, "[IN] Unknown message type: " + message.getClass().getSimpleName());
            }
            unhandled(message);
        }
    }

    @Override
    public void onReceiveRecover(Object message) throws Throwable {
        logger.debug("[" + getSelf().path().name() + "] Received recover " + message.getClass().getSimpleName());
        if (message instanceof PersistentState) { // called when server recovers from durable storage
            persistentState = (PersistentState) message; //buildFromSnapshotOffer((PersistentState) message);
            logger.debug(getSelf().path().name() + " has loaded old " + persistentState.getClass().getSimpleName());
        } else if (message instanceof RecoveryCompleted) {
            logger.debug("[" + getSelf().path().name() + "] Recovery completed: " + persistentState.toString());
            //actor can do something else before processing any other message
        } else {
            logger.error("[" + getSelf().path().name() + "] Unable to process "
                    + message.getClass().getSimpleName() + ". Forwarding to onReceiveCommand()...");
            onReceiveCommand(message);
        }
    }

    public void toFollowerState() {
        logger.debug("[" + getSelf().path().name() + "] toFollowerState");
        state = ServerState.FOLLOWER;
        if (EventNotifier.getInstance() != null) {
            EventNotifier.getInstance().updateState(id, state, persistentState.getCurrentTerm());
        }
        cancelSchedule(heartbeatSchedule); // Required when state changed from LEADER to FOLLOWER
        scheduleElection();
    }

    public void toCandidateState() {
        logger.debug("[" + getSelf().path().name() + "] toCandidateState");
        state = ServerState.CANDIDATE; // c
        if (EventNotifier.getInstance() != null) {
            EventNotifier.getInstance().updateState(id, state, persistentState.getCurrentTerm());
        }
        beginElection(); // e, d
    }

    public void toLeaderState() {
        logger.debug("[" + getSelf().path().name() + "] toLeaderState");
        state = ServerState.LEADER;
        if (EventNotifier.getInstance() != null) {
            EventNotifier.getInstance().updateState(id, state, persistentState.getCurrentTerm());
        }
        leaderId = id;
        cancelSchedule(electionSchedule);
        startHeartbeating();
        // Reinitialize volatile state after election
        initializeNextAndMatchIndex(); //B
        sendNoOp(); // used by the current leader to ensure that current term entry are stored on a majority of servers
    }

    private void sendNoOp() {
        StateMachineCommand nop = new StateMachineCommand(StateMachineCommand.NOOP, null);
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
        cancelSchedule(electionSchedule);
        // Schedule a new election for itself. Starts after ELECTION_TIMEOUT
        int electionTimeout = Util.getRandomElectionTimeout(serverConf.HEARTBEAT_FREQUENCY); // p
        logger.debug(getSelf().path().name() + " election timeout: " + electionTimeout);
        logger.debug("[" + getSelf().path().name() + "] New election timeout: " + electionTimeout);
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
        logger.info("[" + getSelf().path().name() + "] Starting election");
        votersIds.clear();
        persistentState.updateCurrentTerm(this, persistentState.getCurrentTerm() + 1, () -> { // b
            if (EventNotifier.getInstance() != null) {
                EventNotifier.getInstance().updateTerm(id, persistentState.getCurrentTerm());
            }
            leaderId = null;
            getPersistentState().updateVotedFor(this, getSelf(), () -> {
                votersIds.add(getSelf().path().toSerializationFormat()); // f
                scheduleElection(); // g
                int lastLogIndex = persistentState.getLog().size();
                int lastLogTerm = getLastLogTerm(lastLogIndex);
                sendBroadcastRequest(new RequestVoteRequest(persistentState.getCurrentTerm(), lastLogIndex, lastLogTerm));
            });
        });
    }

    private void sendBroadcastRequest(MessageToServer message) {
        logger.debug("[" + getSelf().path().name() + "] [OUT Broadcast] " + message.getClass().getSimpleName());
        EventNotifier.getInstance().addMessage(id, "[OUT Broadcast] " + message.getClass().getSimpleName());
        actorAddresses.forEach(actorAddress -> getContext().actorSelection(actorAddress).tell(message, getSelf()));
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
        String address = Util.getAddressFromId(id, serverConf.CLUSTER_NAME, serverConf.NODES_IPS[id], serverConf.NODES_PORTS[id], serverConf.PREFIX_NODE_NAME);
        return getContext().actorSelection(address);
    }

    public void addEntryToLogAndSendToFollowers(StateMachineCommand command) { //u
        LogEntry entry = new LogEntry(persistentState.getCurrentTerm(), command);
        int lastIndex = persistentState.getLog().size();
        persistentState.updateLog(this, lastIndex + 1, entry, () -> {
            logger.info("[" + getSelf().path().name() + "] Updated log: [" + lastIndex + 1 + "] " + entry);
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
            List<LogEntry> entries = getEntriesInRange(server, nextIndex[followerId], lastIndex);

            if (nextIndex[followerId] > 1) { //at least one entry is already committed
                //previous entry w.r.t. the new ones that has received RegisterClientRequest match in order to accept the new ones
                LogEntry prevEntry = server.getPersistentState().getLog().get(nextIndex[followerId] - 1);
                Integer prevLogTerm = prevEntry.getTermNumber();
                AppendEntriesRequest appendEntriesRequest = new AppendEntriesRequest(server.getPersistentState().getCurrentTerm(), nextIndex[followerId] - 1, prevLogTerm, entries, server.getCommitIndex());
                logger.debug("[" + getSelf().path().name() + "] [OUT] AppendEntriesRequest " + appendEntriesRequest);
                EventNotifier.getInstance().addMessage(id, "[OUT] AppendEntriesRequest " + appendEntriesRequest);
                actor.tell(appendEntriesRequest, getSelf());
            } else { //first entry - previous entry fields are null
                AppendEntriesRequest appendEntriesRequest = new AppendEntriesRequest(server.getPersistentState().getCurrentTerm(), null, null, entries, server.getCommitIndex());
                logger.debug("[" + getSelf().path().name() + "] [OUT] AppendEntriesRequest " + appendEntriesRequest);
                EventNotifier.getInstance().addMessage(id, "[OUT] AppendEntriesRequest " + appendEntriesRequest);
                actor.tell(appendEntriesRequest, getSelf());
            }
        }
    }
    
    private List getEntriesInRange(RaftServer server, int startIndex, int endIndex) {
        List<LogEntry> entries = new ArrayList<>();
        for (int j = startIndex; j <= endIndex; j++) {
            entries.add(server.getPersistentState().getLog().get(j)); // TODO fix bug here
        }
        return entries;
    }

    @Override
    public void onPersistRejected(Throwable cause, Object event, long seqNr) {
        logger.error("[" + getSelf().path().name() + "] Persist has failed with cause " + cause.toString() + "event " + event.toString());
        //turn off the actor here!
    }

    public void checkEntriesToCommit() { // z //call iff leader
        logger.info("[" + getSelf().path().name() + "] Checking if some entries can be committed");
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
        List committedEntries = getEntriesInRange(this, minIndex, maxIndex);
        EventNotifier.getInstance().setCommittedUpTo(id, minIndex, maxIndex, committedEntries);
        for (int i = minIndex; i <= maxIndex; i++) { //v send answer back to the client when committed
            executeCommand(i, leader);
        }
    }

    private void executeCommand(int logIndex, Boolean leader) {
        StateMachineCommand command = persistentState.getLog().get(logIndex).getCommand();

        if (command.getCommand().equals(StateMachineCommand.NOOP)) {
            logger.debug("[" + getSelf().path().name() + "] Received a NO-OP: operation not executed");
            return;
        }

        //registration command of new client - new client session
        if (command.getCommand().equals(RegisterClientRequest.REGISTER)) {
            logger.info("[" + getSelf().path().name() + "] Registering client " + command.getClientAddress());
            EventNotifier.getInstance().addMessage(id, "Registering client " + command.getClientAddress());
            clientSessionMap.put(logIndex, -1); //allocate new session
            if (leader) { //answer back to the client
                RegisterClientResponse registerClientResponse = new RegisterClientResponse(Status.OK, logIndex, null);
                logger.info("[" + getSelf().path().name() + "] [OUT] " + RegisterClientResponse.class.getSimpleName()
                        + " " + registerClientResponse);
                EventNotifier.getInstance().addMessage(id, "[OUT] " + RegisterClientResponse.class.getSimpleName() + " " + registerClientResponse);
                command.getClientAddress().tell(registerClientResponse, getSelf());
            }
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
            logger.info("[" + getSelf().path().name() + "] Committing request: " + command.getCommand() +
                    " of client " + command.getClientId() + " and seqNumber: " + clientSessionMap.get(command.getClientId()));
            EventNotifier.getInstance().addMessage(id, "Committing request: " + command.getCommand() +
                    " of client " + command.getClientId() + " and seqNumber: " + clientSessionMap.get(command.getClientId()));

            if (leader) { //answer back to the client
                ServerResponse serverResponse = new ServerResponse(Status.OK, result, null);
                logger.info("[" + getSelf().path().name() + "] [OUT] " + ServerResponse.class.getSimpleName()
                        + serverResponse);
                EventNotifier.getInstance().addMessage(id, "[OUT] " + ServerResponse.class.getSimpleName() + serverResponse);
                command.getClientAddress().tell(serverResponse, getSelf());
            }
            return;
        }

        //client session is expired, command should not be executed
        logger.info("[" + getSelf().path().name() + "] Client session of " + command.getClientId() + " is expired");
        EventNotifier.getInstance().addMessage(id, "Client session of " + command.getClientId() + " is expired");
        if (leader) {
            ServerResponse serverResponse = new ServerResponse(Status.SESSION_EXPIRED, null, null);
            logger.info("[" + getSelf().path().name() + "] [OUT] " + ServerResponse.class.getSimpleName() + serverResponse);
            EventNotifier.getInstance().addMessage(id, "[OUT] " + ServerResponse.class.getSimpleName() + serverResponse);
            command.getClientAddress().tell(serverResponse, getSelf());
        }
    }

    private String applyToStateMachine(int index) {
        return applyToStateMachine(lastApplied, index);
    }
    
    private String applyToStateMachine(int startIndex, int endIndex) {
        if (startIndex >= endIndex) {
            return persistentState.getLog().get(endIndex).getCommand().getResult();
        }
        startIndex++;
        String result = "(log " + startIndex + ") " + persistentState.getLog().get(startIndex).getCommand().toString();
        logger.info("[" + getSelf().path().name() + "] Applying to state machine. [" + startIndex + "] " + result);
        EventNotifier.getInstance().addMessage(id, "Applying to state machine. [" + startIndex + "] " + result);
        persistentState.getLog().get(startIndex).getCommand().setResult(result);
        lastApplied = startIndex;
        return applyToStateMachine(startIndex, endIndex);
    }

    public int getSenderServerId() {
        String serverName = getSender().path().name(); //e.g. node_0
        return Character.getNumericValue(serverName.charAt(serverName.length() - 1));
    }

    private void initializeActorAddresses() {
        actorAddresses = new LinkedList<>();
        for (int i = 0; i < serverConf.SERVER_NUMBER; i++) {
            if (i != id) {
                String address = Util.getAddressFromId(i, serverConf.CLUSTER_NAME, serverConf.NODES_IPS[i], serverConf.NODES_PORTS[i], serverConf.PREFIX_NODE_NAME);
                actorAddresses.add(address);
            }
        }
    }

    @Override
    public String persistenceId() {
        return "id_" + id;
    }

    //Still here because it could be useful if we decide to use a snapshot to delete old journals
    private PersistentState buildFromSnapshotOffer(SnapshotOffer snapshotOffer) {
        return (PersistentState) snapshotOffer.snapshot();
    }
}
