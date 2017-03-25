package risakka.raft.message.akka;

import akka.actor.ActorRef;
import lombok.AllArgsConstructor;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;
import risakka.raft.message.rpc.server.AppendEntriesRequest;

import java.util.ArrayList;

@AllArgsConstructor
public class SendHeartbeatMessage implements MessageToServer {

    @Override
    public void onReceivedBy(RaftServer server) {
//        System.out.println(server.getSelf().path().name() + " is going to send heartbeat as a LEADER...");
        server.getEventNotifier().addMessage(server.getId(), "[IN] " + this.getClass().getSimpleName());

        sendHeartbeat(server);
    }

    private void sendHeartbeat(RaftServer server) {
        for (ActorRef actorRef : server.getPersistentState().getActorsRefs()) {
            // TODO check if Heartbeat is properly built
            AppendEntriesRequest message = new AppendEntriesRequest(server.getPersistentState().getCurrentTerm(),
                    null, null, new ArrayList<>(), server.getCommitIndex());
            actorRef.tell(message, server.getSelf());
        }
    }
}
