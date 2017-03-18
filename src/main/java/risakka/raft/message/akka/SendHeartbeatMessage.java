package risakka.raft.message.akka;

import lombok.AllArgsConstructor;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;
import risakka.raft.message.rpc.server.AppendEntriesRequest;

import java.util.ArrayList;

@AllArgsConstructor
public class SendHeartbeatMessage implements MessageToServer {

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println(server.getSelf().path().name() + " is going to send heartbeat as a LEADER...");
        // assert state == ServerState.LEADER;
        sendHeartbeat(server);
    }

    private void sendHeartbeat(RaftServer server) {
        // TODO properly build AppendEntriesRequest empty message to make an heartbeat
        server.getBroadcastRouter().route(new AppendEntriesRequest(server.getPersistentState().getCurrentTerm(),
                0, 0, new ArrayList<>(), 0), server.getSelf());
    }
}
