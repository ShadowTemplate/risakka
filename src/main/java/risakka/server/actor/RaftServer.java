package risakka.server.actor;

import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import akka.routing.Router;
import lombok.Getter;
import lombok.Setter;
import risakka.server.message.ElectionTimeoutMessage;
import risakka.server.message.SendHeartbeatMessage;
import risakka.server.raft.*;
import risakka.server.rpc.AppendEntriesRequest;
import risakka.server.rpc.AppendEntriesResponse;
import risakka.server.rpc.RequestVoteRequest;
import risakka.server.rpc.RequestVoteResponse;
import risakka.server.util.Conf;
import risakka.server.util.Util;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;
import risakka.server.message.ClientRequest;
import risakka.server.message.ServerResponse;

@Getter
@Setter
public class RaftServer extends UntypedActor {

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
        persistentState.updateCurrentTerm(persistentState.getCurrentTerm() + 1); // b
        votersIds.add(getSelf().path().toSerializationFormat()); // f
        // TODO change randomly my electionTimeout
        scheduleElection(); // g
        System.out.println(getSelf().path().name() + " will broadcast RequestVoteRequest");
        broadcastRouter.route(new RequestVoteRequest(persistentState.getCurrentTerm(), 0, 0), getSelf()); // h // TODO properly set last 2 params
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
        if (response.getTerm().equals(persistentState.getCurrentTerm()) && response.getVoteGranted()) { // l
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
        if (invocation.getTerm() < persistentState.getCurrentTerm()) { // m
            response = new RequestVoteResponse(persistentState.getCurrentTerm(), false);
        } else if ((persistentState.getVotedFor() == null || persistentState.getVotedFor().equals(getSender())) &&
                isLogUpToDate(invocation.getLastLogIndex(), invocation.getLastLogTerm())) { // n
            // TODO shouldn't we set votedFor now?
            response = new RequestVoteResponse(persistentState.getCurrentTerm(), true);
        } else {
            response = new RequestVoteResponse(persistentState.getCurrentTerm(), false);
        }
        getSender().tell(response, getSelf());
    }

    private boolean isLogUpToDate(Integer candidateLastLogIndex, Integer candidateLastLogTerm) { // t
        return candidateLastLogTerm > persistentState.getCurrentTerm() ||
                (candidateLastLogTerm.equals(persistentState.getCurrentTerm()) && candidateLastLogIndex > persistentState.getLog().size());
    }

    private void onAppendEntriesRequest(AppendEntriesRequest invocation) {
        System.out.println(getSelf().path().name() + " in state " + getState() + " has received AppendEntriesRequest");

        AppendEntriesResponse response;
        if (getState() == State.CANDIDATE) {
            if (invocation.getTerm() >= persistentState.getCurrentTerm()) { // o
                System.out.println(getSelf().path().name() + " recognizes " + getSender().path().name() +
                        " as LEADER and will switch to FOLLOWER state");
                toFollowerState();
            } else {
                response = new AppendEntriesResponse(persistentState.getCurrentTerm(), false, null);
                getSender().tell(response, getSelf());
                return; // reject RPC and remain in CANDIDATE state
            }
        }

        //case FOLLOWER: // s
        //case LEADER: // s // Leader may receive AppendEntries from other (old, isolated) Leaders
        //case [ex-CANDIDATE]
        if (invocation.getTerm() < persistentState.getCurrentTerm() ||
                persistentState.getLog().size() < invocation.getPrevLogIndex() ||
                !persistentState.getLog().get(invocation.getPrevLogIndex()).getTermNumber().equals(invocation.getPrevLogTerm())) {
            response = new AppendEntriesResponse(persistentState.getCurrentTerm(), false, null);
        } else {
            List<LogEntry> newEntries = invocation.getEntries();
            int currIndex = invocation.getPrevLogIndex() + 1;
            for (LogEntry entry : newEntries) {
                if (persistentState.getLog().size() >= currIndex && // there is already an entry in that position
                        !persistentState.getLog().get(currIndex).getTermNumber().equals(entry.getTermNumber())) { // the preexisting entry's term and the new one's are different
                    persistentState.getLog().deleteFrom(currIndex);
                }
                persistentState.getLog().set(currIndex, entry);
                currIndex++;
            }
            if (invocation.getLeaderCommit() > commitIndex) {
                commitIndex = Integer.min(invocation.getLeaderCommit(), currIndex - 1);
            }
            response = new AppendEntriesResponse(persistentState.getCurrentTerm(), true, currIndex - 1);
        }
        getSender().tell(response, getSelf());
    }

    private void sendHeartbeat() {
        broadcastRouter.route(new AppendEntriesRequest(persistentState.getCurrentTerm(), 0, 0, new ArrayList<>(), 0), getSelf());  // TODO properly build AppendEntriesRequest empty message to make an heartbeat
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
        LogEntry entry = new LogEntry(command, persistentState.getCurrentTerm());
        int lastIndex = persistentState.getLog().size();
        persistentState.getLog().set(lastIndex + 1, entry);
    }

    private void sendAppendEntriesToAllFollowers() { //w
        for (int i = 0; i < nextIndex.length; i++) {
            sendAppendEntriesToOneFollower(i);
        }
    }
    
    private void sendAppendEntriesToOneFollower(Integer followerId) { //w
        int lastIndex = persistentState.getLog().size();
        if (lastIndex >= nextIndex[followerId]) {
            //previous entry w.r.t. the new ones that has to match in order to accept the new ones
            LogEntry prevEntry = persistentState.getLog().get(nextIndex[followerId] - 1);
            Integer prevLogTerm = prevEntry.getTermNumber();

            //get new entries
            List<LogEntry> entries = new ArrayList<>();
            for (int j = nextIndex[followerId]; j <= lastIndex; j++) {
                entries.add(persistentState.getLog().get(j));
            }
            //TODO send request to follower i
            new AppendEntriesRequest(persistentState.getCurrentTerm(), nextIndex[followerId] - 1, prevLogTerm, entries, commitIndex);
        }
    }
}
