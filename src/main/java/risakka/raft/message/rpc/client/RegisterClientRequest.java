package risakka.raft.message.rpc.client;

import risakka.raft.actor.RaftServer;
import risakka.raft.log.StateMachineCommand;
import risakka.raft.message.MessageToServer;
import risakka.raft.miscellanea.ServerState;

public class RegisterClientRequest implements MessageToServer {

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println("Server " + server.getSelf().path().name() + " in state " + server.getState() + " has received RegisterClientRequest");
      
        if (server.getState() != ServerState.LEADER) {
            RegisterClientResponse response = new RegisterClientResponse(Status.NOT_LEADER, null, server.getLeaderId());
            server.getSender().tell(response, server.getSelf());
        } else {
            server.addEntryToLogAndSendToFollowers(new StateMachineCommand("Register " + "akka.tcp://raft-cluster@127.0.0.1:20000/user/client", null, null)); //TODO register command sintax: Register actor_address         
            //when the entry will be committed, the client session will be allocated and an answer will be sent back to the client         
        }
        
    }
    
    
}
