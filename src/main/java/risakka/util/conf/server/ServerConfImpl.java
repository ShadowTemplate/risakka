
package risakka.util.conf.server;

import akka.actor.Extension;
import com.typesafe.config.Config;
import risakka.util.Util;

import java.io.File;
import java.lang.reflect.Field;

public class ServerConfImpl implements Extension {
    
    public final String PREFIX_NODE_NAME;
    public final String CLUSTER_NAME;
    public final int SERVER_NUMBER;
    public final int HEARTBEAT_FREQUENCY;  // TODO make sure it is consistent with the avg broadcasting time
    public final int MAX_CLIENT_SESSIONS;
    public final String LOG_FOLDER;
    public final String[] NODES_IPS;
    public final String[] NODES_PORTS;

    public ServerConfImpl(Config config) {
        PREFIX_NODE_NAME = config.getString("servers.PREFIX_NODE_NAME");
        CLUSTER_NAME = config.getString("servers.CLUSTER_NAME");
        SERVER_NUMBER = config.getInt("servers.SERVER_NUMBER");
        HEARTBEAT_FREQUENCY = config.getInt("servers.HEARTBEAT_FREQUENCY");
        MAX_CLIENT_SESSIONS = config.getInt("servers.MAX_CLIENT_SESSIONS");
        LOG_FOLDER = config.getString("servers.LOG_FOLDER");
        
        NODES_IPS = new String[SERVER_NUMBER];
        NODES_PORTS = new String[SERVER_NUMBER];

        for (int i = 0; i < SERVER_NUMBER; i++) {
            NODES_IPS[i] = config.getString("servers.addresses.IP_NODE_" + i); // TODO ADD DEFAULT
            NODES_PORTS[i] = config.getString("servers.addresses.PORT_NODE_" + i); // TODO ADD DEFAULT
        }
        
    }
    
    /**
     * Get the journal folder File instance. 
     * Call only when the configuration has been resolved
     * @param config server configuration
     * @return the File instance of the journal folder 
     */
    public static File getJournalFolder(Config config) {
        return new File(config.getString("akka.persistence.journal.leveldb.dir"));
    }
    
    /**
     * Get the snapshot folder File instance. 
     * Call only when the configuration has been resolved
     * @param config server configuration
     * @return the File instance of the snapshot folder 
     */
    public static File getSnapshotFolder(Config config) {
        return new File(config.getString("akka.persistence.snapshot-store.local.dir"));
    }
    
    public void printConfiguration() {
        System.out.println("Server configuration:");
        Util.printFields(ServerConfImpl.class.getFields(), this);
    }
}

