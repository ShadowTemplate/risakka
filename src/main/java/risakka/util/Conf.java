package risakka.util;

import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.Properties;

public class Conf {

    public static final String CLUSTER_NAME;
    public static final int SERVER_NUMBER;
    public static final int HEARTBEAT_FREQUENCY;  // TODO make sure it is consistent with the avg broadcasting time
    public static final int MAX_CLIENT_SESSIONS;
    
    public static final String[] NODES_IPS;
    public static final String[] NODES_PORTS;
    
    public static final String BASE_DIR;

    static {
        String propertiesFile = "properties";//System.getProperty("risakka");
        System.out.println("Trying to load properties from file: " + propertiesFile);
        Properties prop = new Properties();
        try {
            prop.load(new FileInputStream(propertiesFile));
        } catch (Exception ex) {
            System.err.println("Error while loading properties: " + ex.getMessage());
        }

        CLUSTER_NAME = String.valueOf(prop.getProperty("CLUSTER_NAME", "raft-cluster"));
        SERVER_NUMBER = Integer.valueOf(prop.getProperty("SERVER_NUMBER", "5"));
        HEARTBEAT_FREQUENCY = Integer.valueOf(prop.getProperty("HEARTBEAT_FREQUENCY", "50"));
        MAX_CLIENT_SESSIONS = Integer.valueOf(prop.getProperty("MAX_CLIENT_SESSIONS", "10"));
        
        NODES_IPS = new String[SERVER_NUMBER];
        NODES_PORTS = new String[SERVER_NUMBER];
        
        for (int i = 0; i < SERVER_NUMBER; i++) {
            NODES_IPS[i] = String.valueOf(prop.getProperty("IP_NODE_" + i)); // TODO ADD DEFAULT
            NODES_PORTS[i] = String.valueOf(prop.getProperty("PORT_NODE_" + i)); // TODO ADD DEFAULT
        }
        
        BASE_DIR = System.getProperty("user.dir");

        printConfiguration();
    }

    private static void printConfiguration() {
        System.out.println("Cluster configuration:");
        try {
            for (Field field : Conf.class.getFields()) {
                if (field.getType().isArray()) {
                    Object[] values = (Object[]) field.get(Class[].class);
                    System.out.print("* " + field.getName() + ": [");
                    for (int i = 0; i < values.length - 1; i++) {
                        System.out.print(values[i] + ", ");
                    }
                    System.out.println(values[values.length - 1] + "]");
                } else {
                    System.out.println("* " + field.getName() + ": " + field.get(null));
                }
            }
        } catch (IllegalAccessException ex) {
            System.err.println("Error while displaying properties: " + ex.getMessage());
        }
        System.out.println();
    }
}
