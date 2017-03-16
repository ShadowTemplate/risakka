package risakka.server.message;

import lombok.AllArgsConstructor;
import risakka.server.actor.RaftServer;
import risakka.server.raft.ServerMessage;
import risakka.server.rpc.AppendEntriesRequest;

import java.util.ArrayList;

@AllArgsConstructor
public class SendHeartbeatMessage implements ServerMessage {

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println(server.getSelf().path().name() + " is going to send heartbeat as a LEADER...");
        // assert state == State.LEADER;
        sendHeartbeat(server);
    }

    private void sendHeartbeat(RaftServer server) {
        // TODO properly build AppendEntriesRequest empty message to make an heartbeat
        server.getBroadcastRouter().route(new AppendEntriesRequest(server.getPersistentState().getCurrentTerm(),
                0, 0, new ArrayList<>(), 0), server.getSelf());
    }
}
