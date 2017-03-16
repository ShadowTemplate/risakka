package risakka.server.message;

import akka.actor.ActorRef;
import lombok.AllArgsConstructor;
import risakka.server.actor.RaftClient;
import risakka.server.raft.ClientMessage;
import risakka.server.raft.Status;

@AllArgsConstructor
public class ServerResponse implements ClientMessage {

    private Status status;
    private Integer requestId;
    private ActorRef leader;

    @Override
    public void onReceivedBy(RaftClient client) {
        client.setServer(leader);
        System.out.println("Client " + client.getSelf().path().toSerializationFormat() +
                " has received response from server " + leader.path().toSerializationFormat() +
                " for request " + requestId);
        // TODO implement other logic
    }
}
