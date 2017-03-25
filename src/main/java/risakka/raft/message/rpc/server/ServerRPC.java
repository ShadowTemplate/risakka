package risakka.raft.message.rpc.server;

import risakka.raft.actor.RaftServer;

public class ServerRPC {

    void onProcedureCall(RaftServer server, Integer term) { // A
        if (term > server.getPersistentState().getCurrentTerm()) {
            server.getPersistentState().updateCurrentTerm(server, term, () -> {
                if (server.getEventNotifier() != null) {
                    server.getEventNotifier().updateTerm(server.getId(), term);
                }
                server.toFollowerState();
                server.setLeaderId(null);
            });
        }
    }
}
