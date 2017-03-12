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
import risakka.server.rpc.RequestVoteInvocation;
import risakka.server.rpc.RequestVoteResponse;
import risakka.server.util.Conf;
import scala.concurrent.duration.Duration;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
public class RaftServer extends UntypedActor {
    // Raft paper fields

    // persistent fields  // TODO These 3 fields must be updated on stable storage before responding to RPC
    private Integer currentTerm = 0; // a // TODO check if init with 0 or by loading from the persistent state
    private ActorRef votedFor;
    private LogEntry[] log;

    // volatile fields
    private Integer commitIndex;
    private Integer lastApplied;

    // leader volatile fields // TODO REINITIALIZE AFTER ELECTION
    private Integer[] nextIndex;
    private Integer[] matchIndex;


    // Raft other fields

    // volatile TODO is this right?
    private State state;
    private final int ELECTION_TIMEOUT;
    private Set<String> votersIds;


    // Akka fields

    // volatile fields
    private Router broadcastRouter;
    private Cancellable heartbeatSchedule;
    private Cancellable electionSchedule;


    public RaftServer() {
        ELECTION_TIMEOUT = Conf.HEARTBEAT_FREQUENCY * 10 + (new Random().nextInt(Conf.HEARTBEAT_FREQUENCY / 2));  // TODO check the math
        votersIds = new HashSet<>();
        System.out.println("Election timeout for " + getSelf().path().name() + " is " + ELECTION_TIMEOUT);
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        toFollowerState();
    }

    // TODO Promemoria: rischedulare immediatamente HeartbeatTimeout appena si ricevono notizie dal server.


    public void onReceive(Object message) throws Throwable {
        if (message instanceof SendHeartbeatMessage) {
            System.out.println(getSelf().path().name() + " is going to send heartbeat as a leader...");
            // assert state == State.LEADER;
            broadcastRouter.route(new AppendEntriesInvocation(), getSelf());  // TODO properly build AppendEntriesInvocation empty message to make an heartbeat
        } else if (message instanceof ElectionTimeoutMessage) {
            onElectionTimeoutMessage();
        } else if (message instanceof RequestVoteResponse) {
            onRequestVoteResponse((RequestVoteResponse) message);
        } else if (message instanceof RequestVoteInvocation) {
            onRequestVoteInvocation((RequestVoteInvocation) message);
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
        heartbeatSchedule = getContext().system().scheduler().schedule(Duration.create(0, TimeUnit.MILLISECONDS),
                Duration.create(Conf.HEARTBEAT_FREQUENCY, TimeUnit.MILLISECONDS), getSelf(), new SendHeartbeatMessage(),
                getContext().system().dispatcher(), getSelf());
    }

    private void scheduleElection() {  // TODO remember to reschedule on heartbeat received (call again the method)
        cancelSchedule(electionSchedule);
        // Schedule a new election for itself. Starts after ELECTION_TIMEOUT and repeats every ELECTION_TIMEOUT
        // TODO should repeat every interval or JUST ONCE?
        electionSchedule = getContext().system().scheduler().schedule(Duration.create(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS),
                Duration.create(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS), getSelf(), new ElectionTimeoutMessage(),
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

    private boolean isLogUpToDate(Integer candidateLastLogIndex, Integer candidateLastLogTerm) {
        return new Random().nextBoolean(); // TODO IMPLEMENT LOGIC par 5.2, 5.4
    }
}
