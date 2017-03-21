package risakka.raft.message.rpc.server;

import lombok.AllArgsConstructor;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;
import risakka.raft.miscellanea.ServerState;

@AllArgsConstructor
public class AppendEntriesResponse extends ServerRPC implements MessageToServer {

    private final Integer term;
    private final Boolean success;
    
    //field needed to update nextIndex and matchIndex 
    private final Integer lastEntryIndex;

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println(server.getSelf().path().name() + " in state " + server.getState() + " has received AppendEntriesResponse with success: " + success);

        onProcedureCall(server, term); // A
        
        //leader became follower due to A or received a successful heartbeat
        if(server.getState() != ServerState.LEADER || lastEntryIndex == null) {
            return;
        }
        
        //not a heartbeat
        String followerName = server.getSender().path().name(); //e.g. node_0
        int followerId = Character.getNumericValue(followerName.charAt(followerName.length() - 1));
        System.out.println("follower " + followerId);

        if (success) { //x
            //update nextIndex and matchIndex
            server.updateNextIndexAtIndex(followerId, lastEntryIndex + 1);
            server.updateMatchIndexAtIndex(followerId, lastEntryIndex);

            //check if some entries can be committed
            server.checkEntriesToCommit();

        } else { //y
            //since failed, try again decrementing nextIndex
            server.updateNextIndexAtIndex(followerId, server.getNextIndex()[followerId] - 1);
            server.sendAppendEntriesToOneFollower(server, followerId);
        }

    }
}
