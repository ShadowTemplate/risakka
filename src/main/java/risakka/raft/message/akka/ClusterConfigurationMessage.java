package risakka.raft.message.akka;

import lombok.AllArgsConstructor;
import risakka.gui.EventNotifier;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;

import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
public class ClusterConfigurationMessage implements MessageToServer {

    private final List<String> actors;
    
    @Override
    public void onReceivedBy(RaftServer server) {

        System.out.println(server.getSelf().path().name() + " has received cluster information: " + actors);

        server.getPersistentState().updateActorAddresses(server, actors, () -> {
   
            String logMessage = "[IN] " + this.getClass().getSimpleName() + "\nSize: " + actors.size() + ": " +
                    String.join(", ", actors.stream().collect(Collectors.toList()));

            EventNotifier.getInstance().addMessage(server.getId(), logMessage);
        });
    }
}
