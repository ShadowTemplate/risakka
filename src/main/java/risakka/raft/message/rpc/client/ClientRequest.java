package risakka.raft.message.rpc.client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;
import risakka.raft.log.StateMachineCommand;

@AllArgsConstructor
@Getter
public class ClientRequest implements MessageToServer {

    private Integer requestId;
    private StateMachineCommand command;

    @Override
    public void onReceivedBy(RaftServer server) {  // t
        System.out.println(server.getSelf().path().name() + " in state " + server.getState() + " has received ClientRequestMessage");
        ServerResponse response;

        switch (server.getState()) {
            case LEADER:
                //append entry to local log and send appendEntriesRequest to followers
                server.addEntryToLogAndSendToFollowers(command); //u - w

                //TODO (v) send answer back to the client when committed

                break;

            case FOLLOWER:
            case CANDIDATE:
                //TODO send hint who is the leader
                response = new ServerResponse(Status.NOT_LEADER, null, server.getLeaderId());
                break;
        }

        //getSender().tell(response, getSelf());
    }
}
