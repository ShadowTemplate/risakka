package risakka.raft.message.rpc.client;

import lombok.AllArgsConstructor;
import org.apache.log4j.Logger;
import risakka.raft.actor.RaftClient;
import risakka.raft.message.MessageToClient;

@AllArgsConstructor
public class ServerResponse implements MessageToClient {

    private final Status status;
    private final String response;
    private final Integer leaderHint;
    private static final Logger logger = Logger.getLogger(ServerResponse.class);

    @Override
    public void onReceivedBy(RaftClient client, Object originalClientRequest) {
        logger.info("Client " + client.getSelf().path().name() +
                " has received response " + response +
                " with status " + status);
        
        switch (status) {
            case NOT_LEADER:
                logger.debug("Not leader");
                if (leaderHint != null) {
                    logger.debug("Received an hint. Leader should be " + leaderHint);
                    //send same request to leader hint
                    client.setServerAddress(client.buildAddressFromId(leaderHint));
                    client.sendClientRequest((ClientRequest)originalClientRequest);
                } else {
                    //send same request to a random server
                    logger.debug("Retrying with another server");
                    int randomId = client.getRandomServerId();
                    client.setServerAddress(client.buildAddressFromId(randomId));
                    client.sendClientRequest((ClientRequest)originalClientRequest);
                }
                break;
            case OK:
                logger.info("Command executed successfully. Response: " + response);
                break;
            default:
                logger.error("ServerResponse: Status not recognized");
        }
    }
}
