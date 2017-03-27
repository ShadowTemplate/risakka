package risakka.util;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;


public class Util {

    public static int getRandomElectionTimeout(int heartbeatFrequency) {
        return heartbeatFrequency * 4 + (new Random().nextInt(heartbeatFrequency * 2));
        // TODO check the math. Election timeouts should be chosen randomly from a fixed interval (150-300 ms)
    }
    
    public static String getAddressFromId(int id, String clusterName, String ip, String port, String prefixNode) {
        return "akka.tcp://" + clusterName + "@" + ip + ":" + port + "/user/" + prefixNode + id;
    }

    public static void deleteFolderRecursively(String folderPath) throws IOException {
        Files.walkFileTree(Paths.get(folderPath), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void printFields(Field[] fields, Object object) {
        try {
            for (Field field : fields) {
                if (field.getType().isArray()) {
                    Object[] values = (Object[]) field.get(object);
                    System.out.print("* " + field.getName() + ": [");
                    for (int i = 0; i < values.length - 1; i++) {
                        System.out.print(values[i] + ", ");
                    }
                    System.out.println(values[values.length - 1] + "]");
                } else {
                    System.out.println("* " + field.getName() + ": " + field.get(object));
                }
            }
        } catch (IllegalAccessException ex) {
            System.err.println("Error while displaying properties: " + ex.getMessage());
        }
        System.out.println();
    }
}
