package risakka.server.rpc;

import lombok.AllArgsConstructor;
import risakka.server.actor.RaftServer;
import risakka.server.raft.ServerMessage;

@AllArgsConstructor
public class RequestVoteRequest extends RPC implements ServerMessage {

    private Integer term;
    // candidateId can be retrieved via Akka's getSender() method
    private Integer lastLogIndex;
    private Integer lastLogTerm;

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println(server.getSelf().path().name() + " in state " + server.getState() + " has received RequestVoteRequest");

        onProcedureCall(server, term);  // TODO before tell?

        RequestVoteResponse response;
        if (term < server.getPersistentState().getCurrentTerm()) { // m
            response = new RequestVoteResponse(server.getPersistentState().getCurrentTerm(), false);
        } else if ((server.getPersistentState().getVotedFor() == null || server.getPersistentState().getVotedFor().equals(server.getSender())) &&
                isLogUpToDate(server, lastLogIndex, lastLogTerm)) { // n
            server.getPersistentState().updateVotedFor(server.getSelf());
            response = new RequestVoteResponse(server.getPersistentState().getCurrentTerm(), true);
        } else {
            response = new RequestVoteResponse(server.getPersistentState().getCurrentTerm(), false);
        }
        server.getSender().tell(response, server.getSelf());
    }

    private boolean isLogUpToDate(RaftServer server, Integer candidateLastLogIndex, Integer candidateLastLogTerm) { // t
        return candidateLastLogTerm > server.getPersistentState().getCurrentTerm() ||
                (candidateLastLogTerm.equals(server.getPersistentState().getCurrentTerm()) && candidateLastLogIndex > server.getPersistentState().getLog().size());
    }
}
