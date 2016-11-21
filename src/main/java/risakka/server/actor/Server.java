package risakka.server.actor;

import akka.actor.UntypedActor;
import risakka.server.raft.LogEntry;

public class Server extends UntypedActor {

    // server state

    // persistent fields
    private Integer currentTerm;
    // votedFor
    private LogEntry[] log;

    // volatile fields
    private Integer commitIndex;
    private Integer lastApplied;

    // leader volatile fields
    private Integer[] nextIndex;
    private Integer[] matchIndex;


    public void onReceive(Object message) throws Throwable {
        // on RPC messages
    }
}
