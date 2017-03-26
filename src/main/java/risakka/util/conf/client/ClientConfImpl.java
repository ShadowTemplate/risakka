
package risakka.util.conf.client;

import akka.actor.Extension;
import com.typesafe.config.Config;
import risakka.util.Util;

import java.lang.reflect.Field;

public class ClientConfImpl implements Extension {
    
    public final String SERVER_CLUSTER_NAME;
    public final int SERVER_NUMBER;
    public final String SERVER_PREFIX_NODE_NAME;

    public final String[] NODES_IPS;
    public final String[] NODES_PORTS;

    public final String CLIENT_CLUSTER_NAME;
    public final String CLIENT_NODE_NAME;
    public final int MAX_ATTEMPTS;
    public final int ANSWERING_TIMEOUT;
    
    public ClientConfImpl(Config config) {
        SERVER_CLUSTER_NAME = config.getString("servers.CLUSTER_NAME");
        SERVER_NUMBER = config.getInt("servers.SERVER_NUMBER");
        SERVER_PREFIX_NODE_NAME = config.getString("servers.PREFIX_NODE_NAME");
        
        NODES_IPS = new String[SERVER_NUMBER];
        NODES_PORTS = new String[SERVER_NUMBER];

        for (int i = 0; i < SERVER_NUMBER; i++) {
            NODES_IPS[i] = config.getString("servers.addresses.IP_NODE_" + i); 
            NODES_PORTS[i] = config.getString("servers.addresses.PORT_NODE_" + i); 
        }
        
        CLIENT_CLUSTER_NAME = config.getString("client.CLUSTER_NAME");
        CLIENT_NODE_NAME = config.getString("client.NODE_NAME");
        MAX_ATTEMPTS = config.getInt("client.MAX_ATTEMPTS");
        ANSWERING_TIMEOUT = config.getInt("client.ANSWERING_TIMEOUT");      
    }
    
    public void printConfiguration() {
        System.out.println("Client configuration:");
        Util.printFields(ClientConfImpl.class.getFields(), this);
    }
}

