package risakka.server.message;

import lombok.AllArgsConstructor;
import risakka.server.actor.RaftServer;
import risakka.server.raft.ServerMessage;

@AllArgsConstructor
public class ElectionTimeoutMessage implements ServerMessage {


    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println(server.getSelf().path().name() + " in state " + server.getState() + " has received ElectionTimeoutMessage");
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
