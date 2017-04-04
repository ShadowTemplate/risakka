package risakka.raft.message.rpc.client;

import risakka.raft.miscellanea.EventNotifier;
import risakka.raft.actor.RaftServer;
import risakka.raft.log.StateMachineCommand;
import risakka.raft.message.MessageToServer;

public class RegisterClientRequest implements MessageToServer {
    
    public final static String REGISTER = "Register";

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println("Server " + server.getSelf().path().name() + " in state " + server.getState() + " has received RegisterClientRequest");
        EventNotifier.getInstance().addMessage(server.getId(), "[IN] " + this.getClass().getSimpleName() + " [" + server.getSender().path().name() + "]");
      
        switch (server.getState()) {
            case LEADER:
                //when the entry will be committed, the client session will be allocated and an answer will be sent back to the client
                server.addEntryToLogAndSendToFollowers(new StateMachineCommand(REGISTER, server.getSender()));
                break;
        
            case FOLLOWER:
            case CANDIDATE:
                RegisterClientResponse response = new RegisterClientResponse(Status.NOT_LEADER, null, server.getLeaderId());
                server.getSender().tell(response, server.getSelf());
            break;
        }
                
    }
    
    
}
