package risakka.cluster;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import risakka.gui.ClusterManagerGUI;
import risakka.gui.EventNotifier;
import risakka.raft.message.akka.ClusterConfigurationMessage;

import java.io.File;
import java.io.IOException;
import java.util.*;

import risakka.raft.actor.RaftServer;
import risakka.util.conf.server.ServerConfImpl;
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


        //read configuration without resolving
        Config initial = ConfigFactory.parseResourcesAnySyntax("server_configuration");
        
        //get only Raft properties
        ServerConfImpl conf = new ServerConfImpl(initial);
        conf.printConfiguration();
        
        //create new log folder
        File a = new File(conf.LOG_FOLDER);
        if (a.exists()) {
            Util.deleteFolderRecursively(conf.LOG_FOLDER);
        }

        //for each server resolve config with its id and ip/port
        for (int i = 0; i < conf.SERVER_NUMBER; i++) {
            Config singleNode = ConfigFactory.parseString("servers.ID = " + i + "\n"
                    + "servers.MY_IP = " + conf.NODES_IPS[i] + "\n"
                    + "servers.MY_PORT = " + conf.NODES_PORTS[i]);
            Config total = singleNode.withFallback(initial).resolve();           

            //create folders for journal and snapshots
            ServerConfImpl.getJournalFolder(total).mkdirs();
            ServerConfImpl.getSnapshotFolder(total).mkdirs();

            ActorSystem system = ActorSystem.create(conf.CLUSTER_NAME, total);

            actorSystems.add(system);
            ActorRef actorRef = system.actorOf(Props.create(RaftServer.class, i), conf.PREFIX_NODE_NAME + i);
            actors.put(i, actorRef);
            actorsRefs.add(actorRef);
        }

        ClusterManager clusterManager = new ClusterManager(actorSystems, actors);
        ClusterManagerGUI risakkaGUI = new ClusterManagerGUI(clusterManager);
        EventNotifier.setInstance(risakkaGUI);
        risakkaGUI.run();


        for (ActorRef actor : actorsRefs) {
            actor.tell(new ClusterConfigurationMessage(actorsRefs), actor);
        }
    }

    public List<ActorRef> getMapValues() {
        List<ActorRef> l = new ArrayList<>();


        for (ActorRef actor : actors.values()
                ) {
            l.add(actor);
        }

        return l;

    }
}
