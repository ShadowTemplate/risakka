package risakka.cluster;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import risakka.raft.message.akka.ClusterConfigurationMessage;
import risakka.util.Conf;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import risakka.raft.actor.RaftServer;
import risakka.util.Util;

@AllArgsConstructor
@Getter
public class ClusterManager {

    private final ArrayList<ActorSystem> actorSystems;
    private final Map<Integer, ActorRef> actors;


    public static void main(String[] args) throws IOException {

        // Initializing every Actor with a different actor system, so that avery one has a different
        // IP:port combination and folder where to save its snapshots

        Map<Integer, ActorRef> actors = new HashMap<>();
        ArrayList<ActorSystem> actorSystems = new ArrayList<>();
        List<ActorRef> actorsRefs = new ArrayList<>();

        File a = new File(Conf.LOG_FOLDER + "/");
        if (a.exists()) {
            Util.deleteFolderRecursively(Conf.LOG_FOLDER + "/");
        }

        Config initial = ConfigFactory.load("application");

        for (int i = 0; i < Conf.SERVER_NUMBER; i++) {


            String c = new String(
                    "akka.persistence.journal.leveldb.dir=\"" + Conf.LOG_FOLDER + "/" + i + "/journal\" \n" +
                            "akka.persistence.snapshot-store.local.dir=\"" + Conf.LOG_FOLDER + "/" + i + "/snapshots\" \n" +
                            "akka.remote.netty.tcp.hostname=\"" + Conf.NODES_IPS[i] + "\"\n" +
                            "akka.remote.netty.tcp.port=" + Conf.NODES_PORTS[i] + "\n");

            Config next = ConfigFactory.parseString(c);

            Config total = next.withFallback(initial);

            File f = new File(Conf.LOG_FOLDER + "/" + i + "/journal/");
            File g = new File(Conf.LOG_FOLDER + "/" + i + "/snapshots/");
            f.mkdirs();
            g.mkdirs();

            ActorSystem system = ActorSystem.create(Conf.CLUSTER_NAME, total);


            actorSystems.add(system);
            ActorRef actorRef = system.actorOf(Props.create(RaftServer.class), "node_" + i);
            actors.put(i, actorRef);
            actorsRefs.add(actorRef);
        }

        for (ActorRef actor : actorsRefs) {
            actor.tell(new ClusterConfigurationMessage(actorsRefs), actor);
        }

        ClusterManager clusterManager = new ClusterManager(actorSystems, actors);
        new ClusterManagerGUI(clusterManager).run();


    }


}
