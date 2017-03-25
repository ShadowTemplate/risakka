package risakka.raft.message.rpc.client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;
import risakka.raft.log.StateMachineCommand;

@AllArgsConstructor
@Getter
public class ClientRequest implements MessageToServer {

    private final StateMachineCommand command;

    @Override
    public void onReceivedBy(RaftServer server) {  // t
        System.out.println("Server " + server.getSelf().path().name() + " in state " + server.getState() + " has received ClientRequest");
        server.getEventNotifier().addMessage(server.getId(), "[IN] " + this.getClass().getSimpleName() +
                " [" + server.getSender().path().name() + "]\nCommand: " + command);

        switch (server.getState()) {
            case LEADER:
                //append entry to local log and send appendEntriesRequest to followers
                command.setClientAddress(server.getSender());
                server.addEntryToLogAndSendToFollowers(command); //u - w
                //when the entry will be committed, an answer will be sent back to the client         
                break;

            case FOLLOWER:
            case CANDIDATE:
                ServerResponse response = new ServerResponse(Status.NOT_LEADER, null, server.getLeaderId());
                server.getSender().tell(response, server.getSelf());
                break;
        }

    }
}
