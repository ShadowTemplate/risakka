package risakka.server.util;

import java.io.FileInputStream;
import java.util.Properties;

public class Conf {

    public static final int SERVER_NUMBER;
    public static final int HEARTBEAT_FREQUENCY;  // TODO make sure it is consistent with the avg broadcasting time
    
    public static String[] ipNodes;
    public static String[] portNodes;
    
    public static final String BASE_DIR;
    
    public static void main(String[] args) {
        System.out.println("SERVER_NUMBER: " + SERVER_NUMBER);
        System.out.println("HEARTBEAT_FREQUENCY: " + HEARTBEAT_FREQUENCY);
    }

    static {
        String propertiesFile = System.getProperty("risakka");
        System.out.println("Trying to load: " + propertiesFile);
        Properties prop = new Properties();
        try {
            prop.load(new FileInputStream(propertiesFile));
        } catch (Exception ex) {
            System.err.println("Error while loading properties: " + ex.getMessage());
        }
        SERVER_NUMBER = Integer.valueOf(prop.getProperty("SERVER_NUMBER", "5"));
        HEARTBEAT_FREQUENCY = Integer.valueOf(prop.getProperty("HEARTBEAT_FREQUENCY", "50"));
        
        ipNodes = new String[SERVER_NUMBER];
        portNodes = new String[SERVER_NUMBER];
        
        for (int i = 0; i < SERVER_NUMBER; i++) {
            ipNodes[i] = String.valueOf(prop.getProperty("IP_NODE_" + i));
            portNodes[i] = String.valueOf(prop.getProperty("PORT_NODE_" + i));
        }
        
        BASE_DIR = System.getProperty("user.dir");
    }
}
