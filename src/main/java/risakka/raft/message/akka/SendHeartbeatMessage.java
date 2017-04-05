package risakka.raft.message.akka;

import lombok.AllArgsConstructor;
import org.apache.log4j.Logger;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;
import risakka.raft.message.rpc.server.AppendEntriesRequest;

import java.util.ArrayList;
import risakka.raft.miscellanea.EventNotifier;

@AllArgsConstructor
public class SendHeartbeatMessage implements MessageToServer {

    private static final Logger logger = Logger.getLogger(SendHeartbeatMessage.class);
    @Override
    public void onReceivedBy(RaftServer server) {
        logger.debug(server.getSelf().path().name() + " is going to send heartbeat as a LEADER");
        EventNotifier.getInstance().addMessage(server.getId(), "[IN] " + this.getClass().getSimpleName());

        sendHeartbeat(server);
    }

    private void sendHeartbeat(RaftServer server) {
        server.getActorAddresses().stream()
                .forEach(actorAddress -> {
            AppendEntriesRequest message = new AppendEntriesRequest(server.getPersistentState().getCurrentTerm(),
                    null, null, new ArrayList<>(), server.getCommitIndex());
            server.getContext().actorSelection(actorAddress).tell(message, server.getSelf());
        });
    }
}
