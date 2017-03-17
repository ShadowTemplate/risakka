package risakka.raft.message;

import risakka.raft.actor.RaftServer;

import java.io.Serializable;

public interface ServerMessage extends Serializable {

    void onReceivedBy(RaftServer server);
}
