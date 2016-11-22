package risakka.server.actor;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import risakka.server.raft.LogEntry;
import risakka.server.raft.State;

public class Server extends UntypedActor {

    private State state;

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

    public Server() {
        toFollowerState();
    }

    public void onReceive(Object message) throws Throwable {
        // on RPC messages
    }

    private void toFollowerState() {
        state = State.FOLLOWER;
    }

    private void toCandidateState() {
        state = State.CANDIDATE;
    }

    private void toLeaderState() {
        state = State.LEADER;
    }

}
