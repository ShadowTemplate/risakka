package risakka.raft.message.akka;

import akka.actor.ActorRef;
import akka.routing.Router;
import lombok.AllArgsConstructor;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;
import risakka.util.Util;

import java.util.List;

@AllArgsConstructor
public class ClusterConfigurationMessage implements MessageToServer {

    private final List<ActorRef> actors;

    @Override
    public void onReceivedBy(RaftServer server) {
        System.out.println(server.getSelf().path().name() + " has received cluster information: " + actors);
        server.setActorsRefs(actors);
        server.setBroadcastRouter(Util.buildBroadcastRouter(server.getSelf(), actors));
        server.perstistClusterInfo(actors); //saving cluster nodes Ref to persistent storage
    }
}
