package risakka.server.actor;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import akka.routing.Router;
import com.sun.org.apache.xpath.internal.SourceTree;
import risakka.server.message.ElectionTimeoutMessage;
import risakka.server.message.SendHeartbeatMessage;
import risakka.server.raft.LogEntry;
import risakka.server.raft.State;
import risakka.server.rpc.AppendEntries;
import risakka.server.util.Conf;
import scala.concurrent.duration.Duration;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Server extends UntypedActor {
    // Raft paper fields

    // persistent fields
    private Integer currentTerm;
    private ActorRef votedFor;
    private LogEntry[] log;

    // volatile fields
    private Integer commitIndex;
    private Integer lastApplied;

    // leader volatile fields
    private Integer[] nextIndex;
    private Integer[] matchIndex;


    // Raft other fields

    // volatile TODO right?
    private State state;
    private final int ELECTION_TIMEOUT;


    // Akka fields

    // volatile fields
    private Router broadcastRouter;
    private Cancellable heartbeatSchedule;
    private Cancellable electionSchedule;


    public Server() {
        ELECTION_TIMEOUT = Conf.HEARTBEAT_FREQUENCY * 10 + (new Random().nextInt(Conf.HEARTBEAT_FREQUENCY / 2));  // TODO check the math
        System.out.println("Election timeout for " + getSelf().path().name() + " is " + ELECTION_TIMEOUT);
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        toFollowerState();
    }

    // Promemoria: rischedulare immediatamente HeartbeatTimeout appena si ricevono notizie dal server.
    

    public void onReceive(Object message) throws Throwable {
        // on RPC messages
        if (message instanceof SendHeartbeatMessage) {
            System.out.println(getSelf().path().name() + " is going to send heartbeat as a leader...");
            // assert state == State.LEADER;
            broadcastRouter.route(new AppendEntries(), getSelf());  // TODO properly build AppendEntries empty message to make an heartbeat
        } else if (message instanceof ElectionTimeoutMessage) {
            System.out.println(getSelf().path().name() + " is becoming candidate and will start a new election...");
            toCandidateState();
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
        state = State.CANDIDATE;
        startElection();
    }

    private void toLeaderState() {
        state = State.LEADER;
        startHeartbeating();
    }

    private void startHeartbeating() {  // TODO remember to stop heartbeating when no more leader
        cancelSchedule(heartbeatSchedule);
        heartbeatSchedule = getContext().system().scheduler().schedule(Duration.create(0, TimeUnit.MILLISECONDS),
                Duration.create(Conf.HEARTBEAT_FREQUENCY, TimeUnit.MILLISECONDS), getSelf(), new SendHeartbeatMessage(),
                getContext().system().dispatcher(), getSelf());
    }

    private void scheduleElection() {  // TODO remember to reschedule on heartbeat received (call again the method)
        cancelSchedule(electionSchedule);
        heartbeatSchedule = getContext().system().scheduler().schedule(Duration.create(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS),
                Duration.create(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS), getSelf(), new ElectionTimeoutMessage(),
                getContext().system().dispatcher(), getSelf());
    }

    private void cancelSchedule(Cancellable schedule) {
        if (!schedule.isCancelled()) {
            schedule.cancel();
        }
    }

    private void startElection() {

    }
}
