package risakka.server.raft;

import risakka.server.actor.RaftServer;

import java.io.Serializable;

public interface ServerMessage extends Serializable {

    void onReceivedBy(RaftServer server);
}
