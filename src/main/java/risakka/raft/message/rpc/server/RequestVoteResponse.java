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
        System.out.println(server.getSelf().path().name() + " in state " + server.getState() + " has received RequestVoteResponse");

        onProcedureCall(server, term); // A

        if (term.equals(server.getPersistentState().getCurrentTerm()) && voteGranted) { // l
            System.out.println(server.getSelf().path().name() + " has received vote from " +
                    server.getSender().path().toSerializationFormat());
            server.getVotersIds().add(server.getSender().path().toSerializationFormat());
        }

        if (server.getVotersIds().size() > Conf.SERVER_NUMBER / 2) {
            server.toLeaderState(); // i
        }
    }
}
