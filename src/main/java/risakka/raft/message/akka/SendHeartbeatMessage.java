package risakka.raft.message.akka;

import lombok.AllArgsConstructor;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;
import risakka.raft.message.rpc.server.AppendEntriesRequest;

import java.util.ArrayList;
import risakka.gui.EventNotifier;

@AllArgsConstructor
public class SendHeartbeatMessage implements MessageToServer {

    @Override
    public void onReceivedBy(RaftServer server) {
//        System.out.println(server.getSelf().path().name() + " is going to send heartbeat as a LEADER...");
        EventNotifier.getInstance().addMessage(server.getId(), "[IN] " + this.getClass().getSimpleName());

        sendHeartbeat(server);
    }

    private void sendHeartbeat(RaftServer server) {
        server.getPersistentState().getActorAddresses().stream()
                .filter(actorAddress -> !server.getContext().actorSelection(actorAddress).anchor().equals(server.getSelf()))
                .forEach(actorAddress -> {
            AppendEntriesRequest message = new AppendEntriesRequest(server.getPersistentState().getCurrentTerm(),
                    null, null, new ArrayList<>(), server.getCommitIndex());
            server.getContext().actorSelection(actorAddress).tell(message, server.getSelf());
        });
    }
}
