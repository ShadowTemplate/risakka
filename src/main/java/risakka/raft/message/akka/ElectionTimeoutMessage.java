package risakka.raft.message.akka;

import lombok.AllArgsConstructor;
import risakka.gui.EventNotifier;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;

@AllArgsConstructor
public class ElectionTimeoutMessage implements MessageToServer {

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println(server.getSelf().path().name() + " in state " + server.getState() + " has received ElectionTimeoutMessage");
        EventNotifier.getInstance().addMessage(server.getId(), "[IN] " + this.getClass().getSimpleName());

        switch (server.getState()) {
            case FOLLOWER:
                System.out.println(server.getSelf().path().name() + " will switch to CANDIDATE state");
                server.toCandidateState();
                break;
            case CANDIDATE:
                System.out.println(server.getSelf().path().name() + " will begin new election");
                server.beginElection(); // d
                break;
            default:
                System.err.println("Undefined behaviour for " + server.getState() + " state on ElectionTimeoutMessage");
                break;
        }
    }
}
