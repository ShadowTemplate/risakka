package risakka.raft.message;

import risakka.raft.actor.RaftServer;

import java.io.Serializable;

public interface MessageToServer extends Serializable {

    void onReceivedBy(RaftServer server);
}
