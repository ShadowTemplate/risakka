package risakka.server.rpc;

import lombok.AllArgsConstructor;
import risakka.server.actor.RaftServer;
import risakka.server.raft.ServerMessage;

@AllArgsConstructor
public class AppendEntriesResponse extends RPC implements ServerMessage {

    private Integer term;
    private Boolean success;
    
    //field needed to update nextIndex and matchIndex 
    private Integer lastEntryIndex;

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println(server.getSelf().path().name() + " in state " + server.getState() + " has received AppendEntriesResponse");

        onProcedureCall(server, term); // A

        String serverName = server.getSender().path().name(); //e.g. server0
        int followerId = serverName.charAt(serverName.length() - 1);

        if (success) { //x
            //update nextIndex and matchIndex
            server.getNextIndex()[followerId] = lastEntryIndex;
            server.getMatchIndex()[followerId] = lastEntryIndex;
            
            //check if some entries can be committed
            server.checkEntriesToCommit();
            
        } else { //y
            //since failed, try again decrementing nextIndex
            server.getNextIndex()[followerId] -= 1;
            server.sendAppendEntriesToOneFollower(server, followerId);
        }

    }
}
