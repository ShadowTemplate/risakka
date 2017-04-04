package risakka.raft.message.rpc.server;

import lombok.AllArgsConstructor;
import risakka.raft.miscellanea.EventNotifier;
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
//        System.out.println(server.getSelf().path().name() + " in state " + server.getState() + " has received AppendEntriesResponse with success: " + success);
        if (lastEntryIndex == null) { // heartbeat
            EventNotifier.getInstance().addMessage(server.getId(), "[IN] Heartbeat ACK [" + server.getSender().path().name() + "]");
        } else { // request succeeded or failed
            EventNotifier.getInstance().addMessage(server.getId(), "[IN] " + this.getClass().getSimpleName() + " ["
                    + server.getSender().path().name() + "]\nTerm: " + term + ", success: " + success + ", lastEntryIndex: "
                    + lastEntryIndex);
        }

        onProcedureCall(server, term); // A

        //leader became follower due to A or received a successful heartbeat
        if (server.getState() != ServerState.LEADER || lastEntryIndex == null) {
            return;
        }

        //not a heartbeat
        System.out.println(server.getSelf().path().name() + " in state " + server.getState() + " has received AppendEntriesResponse with success: " + success);
        int followerId = server.getSenderServerId();
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
