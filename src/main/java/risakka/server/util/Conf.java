package risakka.server.util;

import java.io.FileInputStream;
import java.util.Properties;

public class Conf {

    public static final int SERVER_NUMBER;
    public static final double AVG_BROADCAST_TIME;


    public static void main(String[] args) {
        System.out.println("Server number: " + SERVER_NUMBER);
        System.out.println("Avg broadcast time: " + AVG_BROADCAST_TIME);
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
        SERVER_NUMBER = Integer.valueOf(prop.getProperty("server_number", "5"));
        AVG_BROADCAST_TIME = Double.valueOf(prop.getProperty("avg_broadcast_time", "200")); // TODO tune it
    }
}
