package risakka.server.rpc;

import lombok.AllArgsConstructor;
import risakka.server.actor.RaftServer;
import risakka.server.raft.LogEntry;
import risakka.server.raft.ServerMessage;
import risakka.server.raft.State;

import java.util.List;

@AllArgsConstructor
public class AppendEntriesRequest extends RPC implements ServerMessage {

    private Integer term;
    // leaderId can be retrieved via Akka's getSender() method
    private Integer prevLogIndex;
    private Integer prevLogTerm;
    private List<LogEntry> entries;
    private Integer leaderCommit;

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println(server.getSelf().path().name() + " in state " + server.getState() + " has received AppendEntriesRequest");

        AppendEntriesResponse response;
        if (server.getState() == State.CANDIDATE) {
            if (term >= server.getPersistentState().getCurrentTerm()) { // o
                System.out.println(server.getSelf().path().name() + " recognizes " + server.getSender().path().name() +
                        " as LEADER and will switch to FOLLOWER state");
                server.toFollowerState();
            } else {
                response = new AppendEntriesResponse(server.getPersistentState().getCurrentTerm(), false, null);
                server.getSender().tell(response, server.getSelf());
                return; // reject RPC and remain in CANDIDATE state
            }
        }

        //case FOLLOWER: // s
        //case LEADER: // s // Leader may receive AppendEntries from other (old, isolated) Leaders
        //case [ex-CANDIDATE]
        if (term < server.getPersistentState().getCurrentTerm() ||
                server.getPersistentState().getLog().size() < prevLogIndex ||
                !server.getPersistentState().getLog().get(prevLogIndex).getTermNumber().equals(prevLogTerm)) {
            response = new AppendEntriesResponse(server.getPersistentState().getCurrentTerm(), false, null);
        } else {
            int currIndex = prevLogIndex + 1;
            for (LogEntry entry : entries) {
                if (server.getPersistentState().getLog().size() >= currIndex && // there is already an entry in that position
                        !server.getPersistentState().getLog().get(currIndex).getTermNumber().equals(entry.getTermNumber())) { // the preexisting entry's term and the new one's are different
                    server.getPersistentState().deleteLogFrom(currIndex);
                }
                server.getPersistentState().updateLog(currIndex, entry);
                currIndex++;
            }
            if (leaderCommit > server.getCommitIndex()) {
                server.setCommitIndex(Integer.min(leaderCommit, currIndex - 1));
            }
            response = new AppendEntriesResponse(server.getPersistentState().getCurrentTerm(), true, currIndex - 1);
        }
        server.getSender().tell(response, server.getSelf());
        onProcedureCompleted(server, term);  // TODO before tell?
    }
}
