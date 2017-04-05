package risakka.raft.message.akka;

import lombok.AllArgsConstructor;
import org.apache.log4j.Logger;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;

@AllArgsConstructor
public class PauseMessage implements MessageToServer {

    private static final Logger logger = Logger.getLogger(PauseMessage.class);
    @Override
    public void onReceivedBy(RaftServer server) {
        logger.info("[" + server.getSelf().path().name() + "] [onReceive PauseMessage] Becoming pausedActor");
        server.getContext().become(server.getPausedActor());
    }
}
