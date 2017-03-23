package risakka.util;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Properties;

public class Conf {

    private static final String PROPERTIES_FILE = "application.conf";

    public static final String CLUSTER_NAME;
    public static final int SERVER_NUMBER;
    public static final int HEARTBEAT_FREQUENCY;  // TODO make sure it is consistent with the avg broadcasting time

    public static final int MAX_CLIENT_SESSIONS;
    public static final String[] NODES_IPS;
    public static final String[] NODES_PORTS;

    public static final String LOG_FOLDER;
    public static final String BASE_DIR;

    static {
        System.out.println("Trying to load properties from file: " + PROPERTIES_FILE);

        Properties prop = new Properties();
        try {
            URL resource = Conf.class.getClassLoader().getResource(PROPERTIES_FILE);
            File propertiesFile = Paths.get(resource.toURI()).toFile();
            prop.load(new FileInputStream(propertiesFile));
            System.out.println("Configuration successfully loaded from file: " + PROPERTIES_FILE);
        } catch (Exception ex) {
            System.err.println("Error while loading properties: " + ex.getMessage());
            System.err.println("Default configuration will be used instead.");
        }

        CLUSTER_NAME = prop.getProperty("CLUSTER_NAME", "raft-cluster");
        SERVER_NUMBER = Integer.valueOf(prop.getProperty("SERVER_NUMBER", "5"));
        HEARTBEAT_FREQUENCY = Integer.valueOf(prop.getProperty("HEARTBEAT_FREQUENCY", "1000"));
        MAX_CLIENT_SESSIONS = Integer.valueOf(prop.getProperty("MAX_CLIENT_SESSIONS", "10"));
        LOG_FOLDER = prop.getProperty("LOG_FOLDER", "logs");
        NODES_IPS = new String[SERVER_NUMBER];
        NODES_PORTS = new String[SERVER_NUMBER];
        
        for (int i = 0; i < SERVER_NUMBER; i++) {
            NODES_IPS[i] = prop.getProperty("IP_NODE_" + i); // TODO ADD DEFAULT
            NODES_PORTS[i] = prop.getProperty("PORT_NODE_" + i); // TODO ADD DEFAULT
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
