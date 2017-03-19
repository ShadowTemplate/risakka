package risakka.raft.actor;

import akka.actor.ActorSelection;
import akka.actor.Cancellable;
import akka.persistence.RecoveryCompleted;
import akka.persistence.SaveSnapshotFailure;
import akka.persistence.SaveSnapshotSuccess;
import akka.persistence.SnapshotOffer;
import akka.persistence.UntypedPersistentActor;
import akka.routing.Router;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import risakka.raft.log.LogEntry;
import risakka.raft.log.StateMachineCommand;
import risakka.raft.message.MessageToServer;
import risakka.raft.message.akka.ElectionTimeoutMessage;
import risakka.raft.message.akka.SendHeartbeatMessage;
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
    private LRUSessionMap<Integer, ActorSelection> clientSessionMap;

    // Akka fields

    // volatile fields
    private Router broadcastRouter;
    private Cancellable heartbeatSchedule;
    private Cancellable electionSchedule;


    public RaftServer() {
        System.out.println("Creating RaftServer");
        votersIds = new HashSet<>();
        clientSessionMap = new LRUSessionMap<>(Conf.MAX_CLIENT_SESSIONS);
        persistentState = new PersistentState();
        nextIndex = new int[Conf.SERVER_NUMBER];
        matchIndex = new int[Conf.SERVER_NUMBER];
        this.initializeNextAndMatchIndex();
        commitIndex = 0;
        lastApplied = 0;
        leaderId = null;
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        toFollowerState();
    }

    // TODO Promemoria: rischedulare immediatamente HeartbeatTimeout appena si ricevono notizie dal server.

    @Override
    public void onReceiveCommand(Object message) throws Throwable {
        System.out.println(getSelf().path().toSerializationFormat() + " has received command " + message.getClass().getSimpleName());
        if (message instanceof MessageToServer) {
            ((MessageToServer) message).onReceivedBy(this);
        } else if (message instanceof SaveSnapshotSuccess) {
            //Do nothing
        } else if (message instanceof SaveSnapshotFailure) {
            System.out.println("Error while performing the snapshot. " + message);
        } else {
            System.out.println("Unknown message type: " + message.getClass());
            unhandled(message);
        }
    }


    @Override
    public void onReceiveRecover(Object message) throws Throwable {
        System.out.println(getSelf().path().toSerializationFormat() + " has received recover " + message.getClass().getSimpleName());
        if (message instanceof SnapshotOffer) { // called when server recovers from durable storage
            persistentState = (PersistentState) ((SnapshotOffer) message).snapshot();
            System.out.println(getSelf().path().toSerializationFormat() + " has loaded old " + persistentState.getClass().getSimpleName());
        } else if (message instanceof RecoveryCompleted) {
            System.out.println("Recovery completed");
            //actor can do something else before processing any other message
        } else {
            System.out.println(getSelf().path().toSerializationFormat() + " is unable to process "
                    + message.getClass().getSimpleName() + ". Forwarding to onReceiveCommand()...");
            onReceiveCommand(message);
        }
    }

    public void toFollowerState() {
        state = ServerState.FOLLOWER;
        cancelSchedule(heartbeatSchedule); // Required when state changed from LEADER to FOLLOWER
        scheduleElection();
    }

    public void toCandidateState() {
        state = ServerState.CANDIDATE; // c
        onConversionToCandidate(); // e
    }

    public void toLeaderState() {
        state = ServerState.LEADER;
        leaderId = getServerId();
        cancelSchedule(electionSchedule);
        startHeartbeating();

        // Reinitialize volatile state after election
        initializeNextAndMatchIndex(); //B
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

    private void scheduleElection() {  // TODO remember to reschedule on appendEntry received (call again the method)
        // TODO check if, in addition, ElectionTimeoutMessage in Inbox should be removed
        cancelSchedule(electionSchedule);
        // Schedule a new election for itself. Starts after ELECTION_TIMEOUT and repeats every ELECTION_TIMEOUT
        int electionTimeout = Util.getElectionTimeout(); // p
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
        leaderId = null;
        getPersistentState().updateVotedFor(this, getSelf());
        votersIds.add(getSelf().path().toSerializationFormat()); // f
        // TODO change randomly my electionTimeout
        scheduleElection(); // g
        System.out.println(getSelf().path().name() + " will broadcast RequestVoteRequest");
        
        int lastLogIndex = persistentState.getLog().size();
        int lastLogterm;
        if (lastLogIndex > 0) {
            lastLogterm = persistentState.getLog().get(lastLogIndex).getTermNumber();
        } else { //no entry committed yet
            lastLogterm = 0;
        }
        for (int i = 0; i < Conf.SERVER_NUMBER; i++) { //TODO should be broadcast
            if(i != getServerId()) { //not myself
                buildAddressFromId(i).tell(new RequestVoteRequest(persistentState.getCurrentTerm(), lastLogIndex, lastLogterm), getSelf());
            }
        }
//        broadcastRouter.route(new RequestVoteRequest(persistentState.getCurrentTerm(), 0, 0), getSelf()); // h // TODO properly set last 2 params
    }

    // TODO move the following methods in an appropriate location
    
    public void initializeNextAndMatchIndex() { //B
        for (int i = 0; i < nextIndex.length; i++) {
            nextIndex[i] = persistentState.getLog().size() + 1;
            matchIndex[i] = 0;
        }
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
        LogEntry entry = new LogEntry(command, persistentState.getCurrentTerm());
        int lastIndex = persistentState.getLog().size();
        persistentState.updateLog(this, lastIndex + 1, entry);

        sendAppendEntriesToAllFollowers(); //w
    }

    public void sendAppendEntriesToAllFollowers() { //w
        for (int i = 0; i < nextIndex.length; i++) {
            if(i != getServerId()) { //if not myself
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
                entries.add(server.getPersistentState().getLog().get(j));
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
                executeCommands(oldCommitIndex + 1, commitIndex);
                break;
            }
        }
    }

    public void executeCommands(int minIndex, int maxIndex) {
        for (int j = minIndex; j <= maxIndex; j++) { //v send answer back to the client when committed
            StateMachineCommand command = persistentState.getLog().get(j).getCommand();
            if (command.getCommand().startsWith("Register")) {
                String address = command.getCommand().substring(9); //format Register client_address
                clientSessionMap.put(j, getContext().actorSelection(address)); //allocate new session
                clientSessionMap.get(j).tell(new RegisterClientResponse(Status.OK, j, null), getSelf());
            } else if (clientSessionMap.containsKey(command.getClientId())) {
                //TODO execute command on state machine iff command with that seqNumber not already performed 
                String result = "result of command";
                clientSessionMap.get(command.getClientId()).tell(new ServerResponse(Status.OK, result, null), getSelf());
            } else {
                //TODO session expired, command should not be executed
            }
            //TODO update lastApplied
        }
    }

    public int getServerId() {
        String serverName = getSelf().path().name(); //e.g. node_0
        return Character.getNumericValue(serverName.charAt(serverName.length() - 1));
    }

    @Override
    public String persistenceId() {
        return "id_"; // TODO check
    }
}
