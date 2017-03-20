package risakka.raft.message.rpc.server;

import lombok.AllArgsConstructor;
import risakka.raft.actor.RaftServer;
import risakka.raft.log.LogEntry;
import risakka.raft.message.MessageToServer;
import risakka.raft.miscellanea.ServerState;

import java.util.List;

@AllArgsConstructor
public class AppendEntriesRequest extends ServerRPC implements MessageToServer {

    private final Integer term;
    // leaderId can be retrieved via Akka's getSender() method
    private final Integer prevLogIndex;
    private final Integer prevLogTerm;
    private final List<LogEntry> entries;
    private final Integer leaderCommit;

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println(server.getSelf().path().name() + " in state " + server.getState() + " has received AppendEntriesRequest");

        onProcedureCall(server, term); // A

        AppendEntriesResponse response;
        if (server.getState() == ServerState.CANDIDATE && term >= server.getPersistentState().getCurrentTerm()) { // o
            System.out.println(server.getSelf().path().name() + " recognizes " + server.getSender().path().name() +
                    " as LEADER and will switch to FOLLOWER state");
            server.toFollowerState();
        }

        //case FOLLOWER: // s
        //case LEADER: // s // Leader may receive AppendEntries from other (old, isolated) Leaders
        //case [ex-CANDIDATE]
        if (term < server.getPersistentState().getCurrentTerm()) {
            response = new AppendEntriesResponse(server.getPersistentState().getCurrentTerm(), false, null);
        } else if (prevLogIndex != null && (server.getPersistentState().getLog().size() < prevLogIndex || //prevLogIndex == null when log is empty
                !server.getPersistentState().getLog().get(prevLogIndex).getTermNumber().equals(prevLogTerm))) {
            server.setLeaderId(server.getServerId()); //the sender is the leader
            response = new AppendEntriesResponse(server.getPersistentState().getCurrentTerm(), false, null);
        } else {
            int currIndex;
            if(prevLogIndex == null) { //first entry to commit
                currIndex = 1;
            } else {
                currIndex = prevLogIndex + 1;
            }
            for (LogEntry entry : entries) {
                if (server.getPersistentState().getLog().size() >= currIndex && // there is already an entry in that position
                        !server.getPersistentState().getLog().get(currIndex).getTermNumber().equals(entry.getTermNumber())) { // the preexisting entry's term and the new one's are different
                    server.getPersistentState().deleteLogFrom(server, currIndex);
                }
                server.getPersistentState().updateLog(server, currIndex, entry);
                currIndex++;
            }
            if (leaderCommit > server.getCommitIndex()) {
                int oldCommitIndex = server.getCommitIndex();
                server.setCommitIndex(Integer.min(leaderCommit, currIndex - 1));
                server.executeCommands(oldCommitIndex + 1, server.getCommitIndex()); //execute commands known to be committed
            }
            server.setLeaderId(server.getServerId()); //the sender is the leader
            response = new AppendEntriesResponse(server.getPersistentState().getCurrentTerm(), true, currIndex - 1);
        }
        server.getSender().tell(response, server.getSelf());
    }
}
