package risakka.raft.message.rpc.server;

import lombok.AllArgsConstructor;
import lombok.ToString;
import risakka.raft.actor.RaftServer;
import risakka.raft.log.LogEntry;
import risakka.raft.message.MessageToServer;
import risakka.raft.miscellanea.ServerState;

import java.util.List;
import risakka.raft.miscellanea.EventNotifier;

@AllArgsConstructor
@ToString
public class AppendEntriesRequest extends ServerRPC implements MessageToServer {

    private final Integer term;
    // leaderId can be retrieved via Akka's getSender() method
    private final Integer prevLogIndex;
    private final Integer prevLogTerm;
    private final List<LogEntry> entries;
    private final Integer leaderCommit;

    @Override
    public void onReceivedBy(RaftServer server) {
//        System.out.println(server.getSelf().path().name() + " in state " + server.getState() + " has received AppendEntriesRequest");
        if (entries.isEmpty()) {
            EventNotifier.getInstance().addMessage(server.getId(), "[IN] Heartbeat ["
                    + server.getSender().path().name() + "]");
        } else {
            EventNotifier.getInstance().addMessage(server.getId(), "[IN] " + this.getClass().getSimpleName() + " ["
                    + server.getSender().path().name() + "]\nTerm: " + term + ", prevLogTerm: " + prevLogTerm
                    + ", prevLogIndex: " + prevLogIndex + ", leaderCommit: " + leaderCommit + ", entries:\n" + entries);
        }

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

        //AppendEntries (including heartbeat) with older term 
        if (term < server.getPersistentState().getCurrentTerm()) {
            response = new AppendEntriesResponse(server.getPersistentState().getCurrentTerm(), false, null);
            server.getSender().tell(response, server.getSelf());
            return;
        }

        server.setLeaderId(server.getSenderServerId()); //the sender is the leader

        //heartbeat still valid
        if (entries.isEmpty()) {
            server.scheduleElection();
            response = new AppendEntriesResponse(server.getPersistentState().getCurrentTerm(), true, null);
            server.getSender().tell(response, server.getSelf());
            return;
        }

        //AppendEntries (excluding heartbeat) still valid
        
        /* CASE FAIL
        if it is not the first entry in the log
            AND
        (the log has no entry in prevLogIndex OR the terms at prevLogIndex are not equal */
        System.out.println(server.getSelf().path().name() + " in state " + server.getState() + " has received AppendEntriesRequest");
        if (prevLogIndex != null && (server.getPersistentState().getLog().size() < prevLogIndex || //prevLogIndex == null when log is empty
                !server.getPersistentState().getLog().get(prevLogIndex).getTermNumber().equals(prevLogTerm))) {
            response = new AppendEntriesResponse(server.getPersistentState().getCurrentTerm(), false, -1);
            server.getSender().tell(response, server.getSelf());
            return;
        }

        /*CASE SUCCEED*/
        int startIndex = (prevLogIndex == null) ? 1 : prevLogIndex + 1; //null iff it is the first entry to commit
        server.getPersistentState().updateLog(server, startIndex, entries, () -> {
            if (leaderCommit > server.getCommitIndex()) {
                int oldCommitIndex = server.getCommitIndex();
                server.setCommitIndex(Integer.min(leaderCommit, startIndex + entries.size() - 1));
                server.executeCommands(oldCommitIndex + 1, server.getCommitIndex(), false); //execute commands known to be committed
            }
            server.getSender().tell(new AppendEntriesResponse(server.getPersistentState().getCurrentTerm(), true, startIndex + entries.size() - 1), server.getSelf());
        });
    }
}
