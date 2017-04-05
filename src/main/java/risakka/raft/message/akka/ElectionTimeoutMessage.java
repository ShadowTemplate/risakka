package risakka.raft.message.akka;

import lombok.AllArgsConstructor;
import org.apache.log4j.Logger;
import risakka.raft.miscellanea.EventNotifier;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;

@AllArgsConstructor
public class ElectionTimeoutMessage implements MessageToServer {
    private static final Logger logger = Logger.getLogger(ElectionTimeoutMessage.class);

    @Override
    public void onReceivedBy(RaftServer server) {
        logger.info(server.getSelf().path().name() + " in state " + server.getState() + " has received ElectionTimeoutMessage");
        EventNotifier.getInstance().addMessage(server.getId(), "[IN] " + this.getClass().getSimpleName());

        switch (server.getState()) {
            case FOLLOWER:
                logger.info(server.getSelf().path().name() + " will switch to CANDIDATE state");
                server.toCandidateState();
                break;
            case CANDIDATE:
                logger.info(server.getSelf().path().name() + " will begin new election");
                server.beginElection(); // d
                break;
            default:
                logger.error("Undefined behaviour for " + server.getState() + " state on ElectionTimeoutMessage");
                break;
        }
    }
}
