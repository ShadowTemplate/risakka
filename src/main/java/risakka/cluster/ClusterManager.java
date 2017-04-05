package risakka.cluster;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import risakka.gui.ClusterManagerGUI;
import risakka.raft.miscellanea.EventNotifier;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.apache.log4j.Logger;
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
    private static final Logger logger = Logger.getLogger(ClusterManager.class);


    public static void main(String[] args) throws IOException {

        // Initializing every Actor with a different actor system, so that avery one has a different
        // IP:port combination and folder where to save its snapshots

        Boolean singleNode = false;
        Integer id = null;
        switch(args.length) {
            case 0:
                logger.info("Starting cluster");
                break;
            case 1:
                singleNode = true;
                id = Integer.parseInt(args[0]);
                logger.info("Starting single server with id " + id + "...");
                break;
            default:
                logger.error("Error: too many parameters!");
                return;
        }
        
        Map<Integer, String> actors = new HashMap<>();
        ArrayList<ActorSystem> actorSystems = new ArrayList<>();


        //read configuration without resolving
        Config initial = ConfigFactory.parseResourcesAnySyntax("server_configuration");
        
        //get only Raft properties
        ServerConfImpl conf = new ServerConfImpl(initial);
        conf.printConfiguration();
        
        if(singleNode) {
            launchSingleNode(id, initial, conf, actorSystems, actors);
        } else { 
            //create new log folder
            File a = new File(conf.LOG_FOLDER);
            if (a.exists()) {
                logger.debug("Deleting the Logs folder");
                Util.deleteFolderRecursively(conf.LOG_FOLDER);
            }
            
            for (int i = 0; i < conf.SERVER_NUMBER; i++) {
                launchSingleNode(i, initial, conf, actorSystems, actors);
            }
        }

        ClusterManager clusterManager = new ClusterManager(actorSystems, actors, initial, conf);
        ClusterManagerGUI risakkaGUI = new ClusterManagerGUI(clusterManager);
        risakkaGUI.run();
        EventNotifier.setInstance(risakkaGUI);

    }
    
    public static void launchSingleNode(int id, Config initial, ServerConfImpl notResolvedConf, ArrayList<ActorSystem> actorSystems, Map<Integer, String> actors) {
        //resolve config with its id and ip/port
        Config total = resolveConfigurationForId(id, initial, notResolvedConf);

        //create folders for journal and snapshots
        ServerConfImpl.getJournalFolder(total).mkdirs();
        ServerConfImpl.getSnapshotFolder(total).mkdirs();

        //create system, launch actor (the raft server) and store its address
        ActorSystem system = ActorSystem.create(notResolvedConf.CLUSTER_NAME, total);
        system.actorOf(Props.create(RaftServer.class, id), notResolvedConf.PREFIX_NODE_NAME + id);
        String address = Util.getAddressFromId(id, notResolvedConf.CLUSTER_NAME, notResolvedConf.NODES_IPS[id], notResolvedConf.NODES_PORTS[id], notResolvedConf.PREFIX_NODE_NAME);

        actorSystems.add(system);
        actors.put(id, address);
    }
    
    public static Config resolveConfigurationForId(int id, Config initial, ServerConfImpl notResolvedConf) {
        Config singleNode = ConfigFactory.parseString("servers.ID = " + id + "\n"
                + "servers.MY_IP = " + notResolvedConf.NODES_IPS[id] + "\n"
                + "servers.MY_PORT = " + notResolvedConf.NODES_PORTS[id]);
        return singleNode.withFallback(initial).resolve();
    }

}
