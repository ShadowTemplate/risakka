package risakka.raft.message.akka;

import akka.actor.ActorRef;
import lombok.AllArgsConstructor;
import risakka.gui.EventNotifier;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;

import java.util.Collection;
import java.util.stream.Collectors;

@AllArgsConstructor
public class ClusterConfigurationMessage implements MessageToServer {

    private final Collection<ActorRef> actors;

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println(server.getSelf().path().name() + " has received cluster information: " + actors);
        server.getPersistentState().updateActorRefs(server, actors, () -> {
   
            String logMessage = "[IN] " + this.getClass().getSimpleName() + "\nSize: " + actors.size() + ": " +
                    String.join(", ", actors.stream().map(actorRef -> actorRef.path().name()).collect(Collectors.toList()));
            EventNotifier.getInstance().addMessage(server.getId(), logMessage);
        });
    }
}
