package risakka.raft.message.rpc.server;

import lombok.AllArgsConstructor;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;

@AllArgsConstructor
public class RequestVoteRequest extends ServerRPC implements MessageToServer {

    private final Integer term;
    // candidateId can be retrieved via Akka's getSender() method
    private final Integer lastLogIndex;
    private final Integer lastLogTerm;

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println("\n" + server.getSelf().path().name() + " in state " + server.getState() + " has received RequestVoteRequest from " + server.getSender().path().name() + "\n");
        server.getEventNotifier().addMessage(server.getId(), "[IN] " + this.getClass().getSimpleName() + " [" +
                server.getSender().path().name() + "]\nTerm: " + term + ", lastLogTerm: " + lastLogTerm + ", lastLogIndex: " + lastLogIndex);

        onProcedureCall(server, term); // A

        RequestVoteResponse response;
        if (term < server.getPersistentState().getCurrentTerm()) { // m
            System.out.println(server.getSelf().path().name() + " has higher term than and denies vote to " + server.getSender().path().name() + "\n");
            response = new RequestVoteResponse(server.getPersistentState().getCurrentTerm(), false);
        } else if ((server.getPersistentState().getVotedFor() == null || server.getPersistentState().getVotedFor().equals(server.getSender())) &&
                isLogUpToDate(server, lastLogIndex, lastLogTerm)) { // n
            System.out.println(server.getSelf().path().name() + " grants vote to " + server.getSender().path().name() + "\n");
            server.getPersistentState().updateVotedFor(server, server.getSelf());
            server.scheduleElection(); //granting vote --> reschedule election timeout
            response = new RequestVoteResponse(server.getPersistentState().getCurrentTerm(), true);
        } else {
            System.out.println(server.getSelf().path().name() + " has log not matching and denies vote to " + server.getSender().path().name() + "\n");
            response = new RequestVoteResponse(server.getPersistentState().getCurrentTerm(), false);
        }
        server.getSender().tell(response, server.getSelf());
    }

    private boolean isLogUpToDate(RaftServer server, Integer candidateLastLogIndex, Integer candidateLastLogTerm) { // t
        int logSize = server.getPersistentState().getLog().size();
        int lastTerm = server.getLastLogTerm(logSize);
        return candidateLastLogTerm > lastTerm || (candidateLastLogTerm.equals(lastTerm) && candidateLastLogIndex >= logSize);
    }
}
