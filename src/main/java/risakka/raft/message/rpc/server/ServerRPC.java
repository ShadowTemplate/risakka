package risakka.raft.message.rpc.server;

import risakka.raft.actor.RaftServer;

public class ServerRPC {

    public void onProcedureCall(RaftServer server, Integer term) { // A
        if (term > server.getPersistentState().getCurrentTerm()) {
            server.getPersistentState().updateCurrentTerm(term);
            server.toFollowerState();
            server.setLeaderId(null);
        }
    }
}
