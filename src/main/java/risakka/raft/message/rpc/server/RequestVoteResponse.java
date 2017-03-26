package risakka.raft.message.rpc.server;

import lombok.AllArgsConstructor;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;
import risakka.raft.miscellanea.ServerState;

@AllArgsConstructor
public class RequestVoteResponse extends ServerRPC implements MessageToServer {

    private final Integer term;
    private final Boolean voteGranted;

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println("[" + server.getSelf().path().name() + "] [IN] " + RequestVoteResponse.class.getSimpleName() + " in state " + server.getState() + " has received RequestVoteResponse from " + server.getSender().path().name() + "\n");
        server.getEventNotifier().addMessage(server.getId(), "[IN] " + this.getClass().getSimpleName() +
                " [" + server.getSender().path().name() + "]\nTerm: " + term + ", voteGranted: " + voteGranted);

        onProcedureCall(server, term); // A

        if (server.getState() != ServerState.CANDIDATE) { //e.g. became follower due to A
            return;
        }

        if (term.equals(server.getPersistentState().getCurrentTerm()) && voteGranted) { // l
            System.out.println(server.getSelf().path().name() + " has received vote from " +
                    server.getSender().path().name());
            server.getVotersIds().add(server.getSender().path().toSerializationFormat());
        }

        if (server.getVotersIds().size() > server.getServerConf().SERVER_NUMBER / 2) {
            System.out.println(server.getSelf().path().name() + " can now become LEADER");
            server.toLeaderState(); // i
        }

    }
}
