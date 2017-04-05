package risakka.raft.message.rpc.client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.log4j.Logger;
import risakka.raft.actor.RaftClient;
import risakka.raft.message.MessageToClient;

@AllArgsConstructor
@Getter
public class RegisterClientResponse implements MessageToClient {
    
    private final Status status;
    private final Integer clientId;
    private final Integer leaderHint;
    private static final Logger logger = Logger.getLogger(RegisterClientResponse.class);

    @Override
    public void onReceivedBy(RaftClient client, Object originalClientRequest) {
        logger.info("Client " + client.getSelf().path().name() +
                " has received register response with status " + status);    
        
        switch (status) {
            case NOT_LEADER:
                logger.info("Server contacted is not leader");
                if (leaderHint != null) {
                    client.registerContactingSpecificServer(leaderHint);
                } else {
                    //try with a random server
                    client.registerContactingRandomServer();
                }
                break;
            case OK:
                logger.info("ok - client id: " + clientId);
                client.setClientId(clientId);
                break;
            default:
                logger.error("RegisterClientResponse: Status not recognized");
        }
    
    }
    
}
