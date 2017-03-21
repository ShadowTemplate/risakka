package risakka.raft.message.rpc.client;

import lombok.AllArgsConstructor;
import risakka.raft.actor.RaftClient;
import risakka.raft.message.MessageToClient;

@AllArgsConstructor
public class ServerResponse implements MessageToClient {

    private final Status status;
    private final String response;
    private final Integer leaderHint;

    @Override
    public void onReceivedBy(RaftClient client) {
        System.out.println("Client " + client.getSelf().path().name() +
                " has received response " + response +
                " with status " + status);
        
        switch (status) {
            case NOT_LEADER:
                System.out.println("not leader");
                if (leaderHint != null) {
                    //send same request to leader hint
                } else {
                    //send same request to a random server
                }
                break;
            case OK:
                System.out.println("Command executed successfully. Response: " + response);
                break;
            default:
                System.out.println("ServerResponse: Status not recognized");
        }
    }
}
