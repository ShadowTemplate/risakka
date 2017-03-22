package risakka.raft.message.akka;

import akka.actor.ActorRef;
import lombok.AllArgsConstructor;
import risakka.gui.EventNotifier;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;
import risakka.util.Util;

import java.util.Collection;
import java.util.List;

@AllArgsConstructor
public class ClusterConfigurationMessage implements MessageToServer {

    private final Collection<ActorRef> actors;
    private final EventNotifier eventNotifier;

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println(server.getSelf().path().name() + " has received cluster information: " + actors);
        server.setActorsRefs(actors);
        server.setBroadcastRouter(Util.buildBroadcastRouter(server.getSelf(), actors));
        server.persistClusterInfo(actors); //saving cluster nodes Ref to persistent storage
        server.setEventNotifier(eventNotifier);
    }
}
