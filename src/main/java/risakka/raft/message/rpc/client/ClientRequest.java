package risakka.raft.message.rpc.client;

import lombok.AllArgsConstructor;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.ServerMessage;
import risakka.raft.log.StateMachineCommand;

@AllArgsConstructor
public class ClientRequest implements ServerMessage {

    private Integer requestId;
    private StateMachineCommand command;

    @Override
    public void onReceivedBy(RaftServer server) {  // t
        System.out.println(server.getSelf().path().name() + " in state " + server.getState() + " has received ClientRequestMessage");
        ServerResponse response;

        switch (server.getState()) {
            case LEADER:
                //append entry to local log
                server.addEntryToLog(command); //u

                //send appendEntriesRequest to followers
                server.sendAppendEntriesToAllFollowers(); //w

                //TODO (v) send answer back to the client when committed

                break;

            case FOLLOWER:
            case CANDIDATE:
                //TODO send hint who is the leader
                response = new ServerResponse(Status.NOT_LEADER, null, null);
                break;
        }

        //getSender().tell(response, getSelf());
    }
}
