package risakka.raft.message;

import risakka.raft.actor.RaftClient;

import java.io.Serializable;

public interface MessageToClient extends Serializable {

    void onReceivedBy(RaftClient client, Object originalClientRequest);
}
