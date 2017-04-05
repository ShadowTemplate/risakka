package risakka.raft.message.akka;

import lombok.AllArgsConstructor;
import org.apache.log4j.Logger;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;

@AllArgsConstructor
public class ResumeMessage implements MessageToServer {
    private static final Logger logger = Logger.getLogger(ResumeMessage.class);

    @Override
    public void onReceivedBy(RaftServer server) {
        logger.info("[" + server.getSelf().path().name() + "] [onReceive ResumeMessage] Becoming activeActor");
        server.getContext().become(server.getActiveActor());
        server.unstashAll(); // get back all the messages stashed (in the same order in which they were received)
    }
}
