package risakka.raft.message.rpc.client;

import risakka.raft.actor.RaftServer;
import risakka.raft.log.StateMachineCommand;
import risakka.raft.message.MessageToServer;
import risakka.raft.miscellanea.ServerState;

public class RegisterClientRequest implements MessageToServer {

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println(server.getSelf().path().name() + " in state " + server.getState() + " has received RegisterClientRequest");

        RegisterClientResponse response;
        
        if (server.getState() != ServerState.LEADER) {
            response = new RegisterClientResponse(Status.NOT_LEADER, null, server.getLeaderId());
            server.getSender().tell(response, server.getSelf());
        } else {
            server.addEntryToLogAndSendToFollowers(new StateMachineCommand("Register")); //TODO register command sintax?
            
            //TODO allocate session
            
            //TODO (v) send answer back to the client when committed
            //response = new RegisterClientResponse(Status.OK, clientId, null);
                    
        }
        
    }
    
    
}
