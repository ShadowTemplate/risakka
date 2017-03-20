package risakka.raft.message.rpc.client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import risakka.raft.actor.RaftClient;
import risakka.raft.message.MessageToClient;

@AllArgsConstructor
@Getter
public class RegisterClientResponse implements MessageToClient {
    
    private Status status;
    private Integer clientId;
    private Integer leaderHint;

    @Override
    public void onReceivedBy(RaftClient client) {
        System.out.println("Client " + client.getSelf().path().name() +
                " has received register response with status " + status);    
        
        switch (status) {
            case NOT_LEADER:
                System.out.println("not leader");
                if (leaderHint != null) {
                    client.registerContactingSpecificServer(leaderHint);
                } else {
                    //try with a random server
                    client.registerContactingRandomServer();
                }
                break;
            case OK:
                System.out.println("ok");
                break;
            default:
                System.out.println("RegisterClientResponse: Status not recognized");
        }
    
    }
    
}
