package risakka;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import lombok.AllArgsConstructor;
import lombok.Getter;
import risakka.server.util.Conf;

import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
@Getter
public class ClusterManager {

    private final ActorSystem system;
    private final Map<Integer, ActorRef> actors;

    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create(Conf.CLUSTER_NAME);
        Map<Integer, ActorRef> actors = new HashMap<>();
        for (int i = 0; i < Conf.SERVER_NUMBER; i++) {
            actors.put(i, system.actorOf(Props.create(FooNode.class), "server_" + i));
        }
        ClusterManager clusterManager = new ClusterManager(system, actors);
        new ClusterManagerGUI(clusterManager).run();
    }
}
