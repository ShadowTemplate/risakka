package risakka.raft.message.akka;

import lombok.AllArgsConstructor;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;

@AllArgsConstructor
public class PauseMessage implements MessageToServer {

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println("[" + server.getSelf().path().name() + "] [onReceive PauseMessage] Becoming pausedActor");
        server.getContext().become(server.getPausedActor());
    }
}
