package risakka.raft.message.rpc.client;

import akka.actor.ActorRef;
import lombok.AllArgsConstructor;
import risakka.raft.actor.RaftClient;
import risakka.raft.message.MessageToClient;

@AllArgsConstructor
public class ServerResponse implements MessageToClient {

    private Status status;
    private Integer requestId;
    private ActorRef leader;

    @Override
    public void onReceivedBy(RaftClient client) {
//        client.setServer(leader);
        System.out.println("Client " + client.getSelf().path().toSerializationFormat() +
                " has received response from server " + leader.path().toSerializationFormat() +
                " for request " + requestId);
        // TODO implement other logic
    }
}
