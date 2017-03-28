package risakka.cluster;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import risakka.gui.ClusterManagerGUI;
import risakka.gui.EventNotifier;

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
    private final Map<Integer, String> actors;
    private final Config initialConfig;
    private final ServerConfImpl notResolvedConf;


    public static void main(String[] args) throws IOException {

        // Initializing every Actor with a different actor system, so that avery one has a different
        // IP:port combination and folder where to save its snapshots

        Map<Integer, String> actors = new HashMap<>();
        ArrayList<ActorSystem> actorSystems = new ArrayList<>();


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
            Config total = resolveConfigurationForId(i, initial, conf);

            //create folders for journal and snapshots
            ServerConfImpl.getJournalFolder(total).mkdirs();
            ServerConfImpl.getSnapshotFolder(total).mkdirs();

            //create system, launch actor (the raft server) and store its address
            ActorSystem system = ActorSystem.create(conf.CLUSTER_NAME, total);
            system.actorOf(Props.create(RaftServer.class, i), conf.PREFIX_NODE_NAME + i);
            String address = Util.getAddressFromId(i, conf.CLUSTER_NAME, conf.NODES_IPS[i], conf.NODES_PORTS[i], conf.PREFIX_NODE_NAME);
            
            actorSystems.add(system);
            actors.put(i, address);
        }

        ClusterManager clusterManager = new ClusterManager(actorSystems, actors, initial, conf);
        ClusterManagerGUI risakkaGUI = new ClusterManagerGUI(clusterManager);
        risakkaGUI.run();
        EventNotifier.setInstance(risakkaGUI);

    }
    
    public static Config resolveConfigurationForId(int id, Config initial, ServerConfImpl notResolvedConf) {
        Config singleNode = ConfigFactory.parseString("servers.ID = " + id + "\n"
                + "servers.MY_IP = " + notResolvedConf.NODES_IPS[id] + "\n"
                + "servers.MY_PORT = " + notResolvedConf.NODES_PORTS[id]);
        return singleNode.withFallback(initial).resolve();
    }

}
