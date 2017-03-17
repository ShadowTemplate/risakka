package risakka.cluster;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import risakka.util.Conf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

//@AllArgsConstructor
//@Getter
public class ClusterManager {

    private final ArrayList<ActorSystem> actorSystems;
    private final Map<Integer, ActorRef> actors;

    public ClusterManager(ArrayList<ActorSystem> actorSystems, Map<Integer, ActorRef> actors) {
        this.actorSystems=actorSystems;
        this.actors=actors;
    }

    public static void main(String[] args) {

        // Initializing every Actor with a different actor system, so that avery one has a different
        // IP:port combination and folder where to save its snapshots

        Map<Integer, ActorRef> actors = new HashMap<>();
        ArrayList<ActorSystem> actorSystems= new ArrayList<>();

        for (int i = 0; i < Conf.SERVER_NUMBER; i++) {


            String c= new String("akka.persistence.journal.plugin=\"akka.persistence.journal.leveldb\" \n" +
                    "akka.persistence.snapshot-store.plugin=\"akka.persistence.snapshot-store.local\"\n"+
                    "akka.persistence.journal.leveldb.dir=\"logs/"+i+"/journal\" \n"+
                    "akka.persistence.snapshot-store.local.dir=\"logs/"+i+"/snapshots\" \n"+
                    "akka.actor.provider=remote \n"+
                    "akka.remote.netty.tcp.hostname=\""+Conf.NODES_IPS[i]+"\"\n" +
                    "akka.remote.netty.tcp.port="+Conf.NODES_PORTS[i] + "\n" +
                    "akka.remote.enabled-transports=[\"akka.remote.netty.tcp\"] \n");

            Config config= ConfigFactory.parseString(c);

            ActorSystem system = ActorSystem.create(Conf.CLUSTER_NAME, config);

            actorSystems.add(system);
            actors.put(i, system.actorOf(Props.create(FooNode.class, i), "node"));
        }
        ClusterManager clusterManager = new ClusterManager(actorSystems, actors);
        new ClusterManagerGUI(clusterManager).run();
    }

    public ActorSystem getSystem(int nodeId) { return this.actorSystems.get(nodeId); }

    public Map<Integer, ActorRef> getActors(){ return this.actors;}
}
