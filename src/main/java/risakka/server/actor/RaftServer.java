package risakka.server.actor;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import akka.routing.Router;
import lombok.Getter;
import lombok.Setter;
import risakka.server.message.ElectionTimeoutMessage;
import risakka.server.message.SendHeartbeatMessage;
import risakka.server.persistence.Durable;
import risakka.server.raft.LogEntry;
import risakka.server.raft.State;
import risakka.server.rpc.AppendEntriesRequest;
import risakka.server.rpc.AppendEntriesResponse;
import risakka.server.rpc.RequestVoteRequest;
import risakka.server.rpc.RequestVoteResponse;
import risakka.server.util.Conf;
import risakka.server.util.SequentialContainer;
import risakka.server.util.Util;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;
import risakka.server.message.ClientRequest;
import risakka.server.message.ServerResponse;
import risakka.server.raft.StateMachineCommand;
import risakka.server.raft.Status;

@Getter
@Setter
public class RaftServer extends UntypedActor {

    private class PersistentState implements Durable {
        // persistent fields  // TODO These 3 fields must be updated on stable storage before responding to RPC
        private Integer currentTerm = 0; // a // TODO check if init with 0 or by loading from the persistent state
        private ActorRef votedFor;  // TODO reset to null after on currentTerm change?
        private SequentialContainer<LogEntry> log;  // first index is 1

        // TODO SETTER WITH UPDATE ON PERS STORAGE
    }


    // Raft paper fields
    private PersistentState persistentState;

    // volatile fields
    private Integer commitIndex;
    private Integer lastApplied;

    // leader volatile fields // TODO REINITIALIZE AFTER ELECTION
    private Integer[] nextIndex;
    private Integer[] matchIndex;


    // Raft other fields

    // volatile TODO is this right?
    private State state; // FOLLOWER / CANDIDATE / LEADER
    private Set<String> votersIds;


    // Akka fields

    // volatile fields
    private Router broadcastRouter;
    private Cancellable heartbeatSchedule;
    private Cancellable electionSchedule;


    public RaftServer() {
        System.out.println("Creating RaftServer");
        votersIds = new HashSet<>();
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        toFollowerState();
    }

    // TODO Promemoria: rischedulare immediatamente HeartbeatTimeout appena si ricevono notizie dal server.

    public void onReceive(Object message) throws Throwable {
        if (message instanceof SendHeartbeatMessage) {
            System.out.println(getSelf().path().name() + " is going to send heartbeat as a LEADER...");
            // assert state == State.LEADER;
            sendHeartbeat();
        } else if (message instanceof ElectionTimeoutMessage) {
            onElectionTimeoutMessage();
        } else if (message instanceof RequestVoteRequest) {
            onRequestVoteRequest((RequestVoteRequest) message);
        } else if (message instanceof RequestVoteResponse) {
            onRequestVoteResponse((RequestVoteResponse) message);
        } else if (message instanceof AppendEntriesRequest) {
            onAppendEntriesRequest((AppendEntriesRequest) message);
        } else if (message instanceof AppendEntriesResponse) {
            onAppendEntriesResponse((AppendEntriesResponse) message);
        } else if (message instanceof ClientRequest) {
            onClientRequest((ClientRequest) message);
        } else {
            System.out.println("Unknown message type: " + message.getClass());
            unhandled(message);
        }
    }

    private void toFollowerState() {
        state = State.FOLLOWER;
        scheduleElection();
    }

    private void toCandidateState() {
        state = State.CANDIDATE; // c
        onConversionToCandidate(); // e
    }

    private void toLeaderState() {
        state = State.LEADER;
        cancelSchedule(electionSchedule);
        startHeartbeating();
    }

    private void startHeartbeating() {  // TODO remember to stop heartbeating when no more leader
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

    private void beginElection() { // d
        votersIds.clear();
        persistentState.currentTerm++; // b
        votersIds.add(getSelf().path().toSerializationFormat()); // f
        // TODO change randomly my electionTimeout
        scheduleElection(); // g
        System.out.println(getSelf().path().name() + " will broadcast RequestVoteRequest");
        broadcastRouter.route(new RequestVoteRequest(persistentState.currentTerm, 0, 0), getSelf()); // h // TODO properly set last 2 params
    }

    private void onElectionTimeoutMessage() {
        System.out.println(getSelf().path().name() + " in state " + getState() + " has received ElectionTimeoutMessage");
        switch (getState()) {
            case FOLLOWER:
                System.out.println(getSelf().path().name() + " will switch to CANDIDATE state");
                toCandidateState();
                break;
            case CANDIDATE:
                System.out.println(getSelf().path().name() + " will begin new election");
                beginElection(); // d
                break;
            default:
                System.err.println("Undefined behaviour for " + getState() + " state on ElectionTimeoutMessage");
                break;
        }
    }

    private void onRequestVoteResponse(RequestVoteResponse response) {
        System.out.println(getSelf().path().name() + " in state " + getState() + " has received RequestVoteResponse");
        if (response.getTerm().equals(persistentState.currentTerm) && response.getVoteGranted()) { // l
            System.out.println(getSelf().path().name() + " has received vote from " +
                    getSender().path().toSerializationFormat());
            votersIds.add(getSender().path().toSerializationFormat());
        }
        if (votersIds.size() > Conf.SERVER_NUMBER / 2) {
            toLeaderState(); // i
        }
    }

    private void onRequestVoteRequest(RequestVoteRequest invocation) {
        System.out.println(getSelf().path().name() + " in state " + getState() + " has received RequestVoteRequest");
        RequestVoteResponse response;
        if (invocation.getTerm() < persistentState.currentTerm) { // m
            response = new RequestVoteResponse(persistentState.currentTerm, false);
        } else if ((persistentState.votedFor == null || persistentState.votedFor.equals(getSender())) &&
                isLogUpToDate(invocation.getLastLogIndex(), invocation.getLastLogTerm())) { // n
            response = new RequestVoteResponse(persistentState.currentTerm, true);
        } else {
            response = new RequestVoteResponse(persistentState.currentTerm, false);
        }
        getSender().tell(response, getSelf());
    }

    private boolean isLogUpToDate(Integer candidateLastLogIndex, Integer candidateLastLogTerm) { // t
        return candidateLastLogTerm > persistentState.currentTerm ||
                (candidateLastLogTerm.equals(persistentState.currentTerm) && candidateLastLogIndex > persistentState.log.size());
    }

    private void onAppendEntriesRequest(AppendEntriesRequest invocation) {
        System.out.println(getSelf().path().name() + " in state " + getState() + " has received AppendEntriesRequest");

        AppendEntriesResponse response;
        if (getState() == State.CANDIDATE) {
            if (invocation.getTerm() >= persistentState.currentTerm) { // o
                System.out.println(getSelf().path().name() + " recognizes " + getSender().path().name() +
                        " as LEADER and will switch to FOLLOWER state");
                toFollowerState();
            } else {
                response = new AppendEntriesResponse(persistentState.currentTerm, false, null);
                getSender().tell(response, getSelf());
                return; // reject RPC and remain in CANDIDATE state
            }
        }

        //case FOLLOWER: // s
        //case LEADER: // s // Leader may receive AppendEntries from other (old, isolated) Leaders
        //case [ex-CANDIDATE]
        if (invocation.getTerm() < persistentState.currentTerm ||
                persistentState.log.size() < invocation.getPrevLogIndex() ||
                !persistentState.log.get(invocation.getPrevLogIndex()).getTermNumber().equals(invocation.getPrevLogTerm())) {
            response = new AppendEntriesResponse(persistentState.currentTerm, false, null);
        } else {
            List<LogEntry> newEntries = invocation.getEntries();
            int currIndex = invocation.getPrevLogIndex() + 1;
            for (LogEntry entry : newEntries) {
                if (persistentState.log.size() >= currIndex && // there is already an entry in that position
                        !persistentState.log.get(currIndex).getTermNumber().equals(entry.getTermNumber())) { // the preexisting entry's term and the new one's are different
                    persistentState.log.deleteFrom(currIndex);
                }
                persistentState.log.set(currIndex, entry);
                currIndex++;
            }
            if (invocation.getLeaderCommit() > commitIndex) {
                commitIndex = Integer.min(invocation.getLeaderCommit(), currIndex - 1);
            }
            response = new AppendEntriesResponse(persistentState.currentTerm, true, currIndex - 1);
        }
        getSender().tell(response, getSelf());
    }

    private void sendHeartbeat() {
        broadcastRouter.route(new AppendEntriesRequest(persistentState.currentTerm, 0, 0, new ArrayList<>(), 0), getSelf());  // TODO properly build AppendEntriesRequest empty message to make an heartbeat
    }

    private void onAppendEntriesResponse(AppendEntriesResponse message) {
        String serverName = getSender().path().name(); //e.g. server0
        int followerId = serverName.charAt(serverName.length() - 1);
        
        if (message.getSuccess()) { //x      
            //update nextIndex and matchIndex
            nextIndex[followerId] = message.getLastEntryIndex();
            matchIndex[followerId] = message.getLastEntryIndex();
        } else { //y
            //since failed, try again decrementing nextIndex
            nextIndex[followerId] -= 1;
            sendAppendEntriesToOneFollower(followerId);
        }
    }

    private void onClientRequest(ClientRequest message) { //t
        System.out.println(getSelf().path().name() + " in state " + getState() + " has received ClientRequestMessage");
        ServerResponse response;

        switch (getState()) {
            case LEADER:
                //append entry to local log
                addEntryToLog(message.getCommand()); //u

                //send appendEntriesRequest to followers
                sendAppendEntriesToAllFollowers(); //w
                
                //TODO (v) send answer back to the client when committed

                break;
            
            case FOLLOWER:
            case CANDIDATE:
                //TODO send hint who is the leader
                response = new ServerResponse(Status.NOT_LEADER, null, null);
                break;
        }

        //getSender().tell(response, getSelf());
    }

    private void addEntryToLog(StateMachineCommand command) { //u
        LogEntry entry = new LogEntry(command, persistentState.currentTerm);
        int lastIndex = persistentState.log.size();
        persistentState.log.set(lastIndex + 1, entry);
    }

    private void sendAppendEntriesToAllFollowers() { //w
        for (int i = 0; i < nextIndex.length; i++) {
            sendAppendEntriesToOneFollower(i);
        }
    }
    
    private void sendAppendEntriesToOneFollower(Integer followerId) { //w
        int lastIndex = persistentState.log.size();
        if (lastIndex >= nextIndex[followerId]) {
            //previous entry w.r.t. the new ones that has to match in order to accept the new ones
            LogEntry prevEntry = persistentState.log.get(nextIndex[followerId] - 1);
            Integer prevLogTerm = prevEntry.getTermNumber();

            //get new entries
            List<LogEntry> entries = new ArrayList<>();
            for (int j = nextIndex[followerId]; j <= lastIndex; j++) {
                entries.add(persistentState.log.get(j));
            }
            //TODO send request to follower i
            new AppendEntriesRequest(persistentState.currentTerm, nextIndex[followerId] - 1, prevLogTerm, entries, commitIndex);
        }
    }
}
