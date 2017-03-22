package risakka.raft.message.rpc.server;

import lombok.AllArgsConstructor;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;
import risakka.util.Conf;

@AllArgsConstructor
public class RequestVoteResponse extends ServerRPC implements MessageToServer {

    private final Integer term;
    private final Boolean voteGranted;

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println("\n" + server.getSelf().path().name() + " in state " + server.getState() + " has received RequestVoteResponse from " + server.getSender().path().name() + "\n");

        onProcedureCall(server, term); // A


        if (term.equals(server.getPersistentState().getCurrentTerm()) && voteGranted) { // l
            System.out.println(server.getSelf().path().name() + " has received vote from " +
                    server.getSender().path().name());
            server.getVotersIds().add(server.getSender().path().toSerializationFormat());
        }

        if (server.getVotersIds().size() > Conf.SERVER_NUMBER / 2) {
            System.out.println(server.getSelf().path().name() + " can now become LEADER");
            server.toLeaderState(); // i
        } else if (!voteGranted) {
            System.out.println("\n" + server.getSelf().path().name() + " Received a voteGranted==FALSE. Switching to follower");
            server.toFollowerState();
        }

    }
}
