package risakka.server.rpc;

import lombok.AllArgsConstructor;
import risakka.server.actor.RaftServer;
import risakka.server.raft.ServerMessage;
import risakka.server.util.Conf;

@AllArgsConstructor
public class RequestVoteResponse extends RPC implements ServerMessage {

    private Integer term;
    private Boolean voteGranted;

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println(server.getSelf().path().name() + " in state " + server.getState() + " has received RequestVoteResponse");

        onProcedureCall(server, term);

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
