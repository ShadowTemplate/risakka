package risakka.server.actor;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import akka.routing.Router;
import lombok.Getter;
import lombok.Setter;
import risakka.server.message.ElectionTimeoutMessage;
import risakka.server.message.SendHeartbeatMessage;
import risakka.server.raft.LogEntry;
import risakka.server.raft.State;
import risakka.server.rpc.AppendEntriesInvocation;
import risakka.server.rpc.AppendEntriesResponse;
import risakka.server.rpc.RequestVoteInvocation;
import risakka.server.rpc.RequestVoteResponse;
import risakka.server.util.Conf;
import risakka.server.util.SequentialContainer;
import risakka.server.util.Util;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
public class RaftServer extends UntypedActor {
    // Raft paper fields

    // persistent fields  // TODO These 3 fields must be updated on stable storage before responding to RPC
    private Integer currentTerm = 0; // a // TODO check if init with 0 or by loading from the persistent state
    private ActorRef votedFor;  // TODO reset to null after on currentTerm change?
    private SequentialContainer<LogEntry> log;  // first index is 1

    // volatile fields
    private Integer commitIndex;
    private Integer lastApplied;

    // leader volatile fields // TODO REINITIALIZE AFTER ELECTION
    private Integer[] nextIndex;
    private Integer[] matchIndex;


    // Raft other fields

    // volatile TODO is this right?
    private State state;
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
        } else if (message instanceof RequestVoteResponse) {
            onRequestVoteResponse((RequestVoteResponse) message);
        } else if (message instanceof RequestVoteInvocation) {
            onRequestVoteInvocation((RequestVoteInvocation) message);
        } else if (message instanceof AppendEntriesInvocation) {
            onAppendEntriesInvocation((AppendEntriesInvocation) message);
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
                Duration.create(0, TimeUnit.MILLISECONDS), // q
                Duration.create(Conf.HEARTBEAT_FREQUENCY, TimeUnit.MILLISECONDS), getSelf(), new SendHeartbeatMessage(), // q
                getContext().system().dispatcher(), getSelf());
    }

    private void scheduleElection() {  // TODO remember to reschedule on heartbeat received (call again the method)
        cancelSchedule(electionSchedule);
        // Schedule a new election for itself. Starts after ELECTION_TIMEOUT and repeats every ELECTION_TIMEOUT
        // TODO should repeat every interval or JUST ONCE?
        int electionTimeout = Util.getElectionTimeout(); // p
        electionSchedule = getContext().system().scheduler().schedule(
                Duration.create(electionTimeout, TimeUnit.MILLISECONDS),
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
        currentTerm++; // b
        votersIds.add(getSelf().path().toSerializationFormat()); // f
        scheduleElection(); // g
        System.out.println(getSelf().path().name() + " will broadcast RequestVoteInvocation");
        broadcastRouter.route(new RequestVoteInvocation(currentTerm, 0, 0), getSelf()); // h // TODO properly set last 2 params
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
        if (response.getTerm().equals(currentTerm) && response.getVoteGranted()) { // l
            System.out.println(getSelf().path().name() + " has received vote from " +
                    getSender().path().toSerializationFormat());
            votersIds.add(getSender().path().toSerializationFormat());
        }
        if (votersIds.size() > Conf.SERVER_NUMBER / 2) {
            toLeaderState(); // i
        }
    }

    private void onRequestVoteInvocation(RequestVoteInvocation invocation) {
        System.out.println(getSelf().path().name() + " in state " + getState() + " has received RequestVoteInvocation");
        RequestVoteResponse response;
        if (invocation.getTerm() < currentTerm) { // m
            response = new RequestVoteResponse(currentTerm, false);
        } else if ((votedFor == null || votedFor.equals(getSender())) &&
                isLogUpToDate(invocation.getLastLogIndex(), invocation.getLastLogTerm())) { // n
            response = new RequestVoteResponse(currentTerm, true);
        } else {
            response = new RequestVoteResponse(currentTerm, false);
        }
        getSender().tell(response, getSelf());
    }

    private boolean isLogUpToDate(Integer candidateLastLogIndex, Integer candidateLastLogTerm) { // t
        return candidateLastLogTerm > currentTerm ||
                (candidateLastLogTerm.equals(currentTerm) && candidateLastLogIndex > log.size());
    }

    private void onAppendEntriesInvocation(AppendEntriesInvocation invocation) {
        System.out.println(getSelf().path().name() + " in state " + getState() + " has received AppendEntriesInvocation");
        switch (getState()) {
            case FOLLOWER:  // s
                AppendEntriesResponse response;
                if (invocation.getTerm() < currentTerm || log.size() < invocation.getPrevLogIndex()) {
                    // TODO I added the 2nd check. Maybe it's unnecessary, but an AIOOBException could be thrown otherwise later
                    response = new AppendEntriesResponse(currentTerm, false);
                } else if (!log.get(invocation.getPrevLogIndex()).getTermNumber().equals(invocation.getPrevLogTerm())) {
                    response = new AppendEntriesResponse(currentTerm, false);
                } else {
                    List<LogEntry> newEntries = invocation.getEntries();
                    for (LogEntry entry : newEntries) {
                        if (log.size() >= entry.getPositionInLog() && // there is already an entry in that position
                                !log.get(entry.getPositionInLog()).getTermNumber().equals(entry.getTermNumber())) { // the preexisting entry's term and the new one's are different
                            log.deleteFrom(entry.getPositionInLog());
                        }
                        log.set(entry.getPositionInLog(), entry);
                    }
                    if (invocation.getLeaderCommit() > commitIndex) {
                        commitIndex = Integer.min(invocation.getLeaderCommit(),
                                newEntries.get(newEntries.size() - 1).getPositionInLog());
                    }
                    response = new AppendEntriesResponse(currentTerm, true);
                }
                getSender().tell(response, getSelf());
                break;
            case CANDIDATE:
                if (invocation.getTerm() >= currentTerm) { // o
                    System.out.println(getSelf().path().name() + " recognizes " + getSender().path().name() +
                            " as LEADER and will switch to FOLLOWER state");
                    toFollowerState();
                } // else: reject RPC and remain in CANDIDATE state
                break;
            default:
                // TODO
                break;
        }
    }

    private void sendHeartbeat() {
        broadcastRouter.route(new AppendEntriesInvocation(currentTerm, 0, 0, new ArrayList<>(), 0), getSelf());  // TODO properly build AppendEntriesInvocation empty message to make an heartbeat
    }
}
