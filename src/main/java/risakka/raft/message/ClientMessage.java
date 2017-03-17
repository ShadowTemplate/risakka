package risakka.raft.message;

import risakka.raft.actor.RaftClient;

import java.io.Serializable;

public interface ClientMessage extends Serializable {

    void onReceivedBy(RaftClient client);
}
