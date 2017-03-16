package risakka.server.rpc;

import risakka.server.actor.RaftServer;

public class RPC {

    public void onProcedureCompleted(RaftServer server, Integer term) { // A
        if (term > server.getPersistentState().getCurrentTerm()) {
            server.getPersistentState().updateCurrentTerm(term);
            server.toFollowerState();
        }
    }
}
