package risakka.server.raft;

import risakka.server.actor.RaftClient;

import java.io.Serializable;

public interface ClientMessage extends Serializable {

    void onReceivedBy(RaftClient client);
}
