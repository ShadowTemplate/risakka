package risakka.util;

import akka.actor.*;
import akka.routing.ActorRefRoutee;
import akka.routing.BroadcastRoutingLogic;
import akka.routing.Routee;
import akka.routing.Router;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static java.util.stream.Collectors.toMap;

public class Util {

    public static int getElectionTimeout() {
        return Conf.HEARTBEAT_FREQUENCY * 10 + (new Random().nextInt(Conf.HEARTBEAT_FREQUENCY / 2));
        // TODO check the math. Election timeouts should be chosen randomly from a fixed interval (150-300 ms)
    }

    public static Router buildBroadcastRouter(ActorRef actor, List<ActorRef> actors) {
        Map<ActorPath, Routee> routeeList = actors.parallelStream().collect(toMap(ActorRef::path, ActorRefRoutee::new));
        routeeList.remove(actor.path());
        return new Router(new BroadcastRoutingLogic(), routeeList.values());
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

    /*
    public static List<ActorRef> buildActorsRefs(UntypedActorContext context, String[] nodesIPs, String[] nodesPorts) {
        List<ActorRef> actorsRefs = new ArrayList<>();
        for (int i = 0; i < nodesIPs.length; i++) {
            actorsRefs.add(buildActorRef(context, nodesIPs[i], nodesPorts[i]));
        }
        return actorsRefs;
    }
    */



    /*
    // TODO remove
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("routers-creation-test");
        final int serverNumber = 5;
        List<ActorRef> serverList = new ArrayList<>(serverNumber);
        for (int i = 0; i < serverNumber; i++) {
            serverList.add(system.actorOf(Props.create(DemoServer.class), "server" + i));
        }
        Map<ActorPath, Router> actorPathRouterMap = buildBroadcastRoutersMap(serverList);
        for (Map.Entry<ActorPath, Router> actorPathRouterEntry : actorPathRouterMap.entrySet()) {
            System.out.println("" + actorPathRouterEntry.getKey().toSerializationFormat() +
                    "\n" + actorPathRouterEntry.getValue().routees());
        }
        system.shutdown();
    }

    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("routers-creation-test");
        final int serverNumber = 3;
        List<ActorRef> serverList = new ArrayList<>(serverNumber);
        for (int i = 0; i < serverNumber; i++) {
            serverList.add(system.actorOf(Props.create(RaftServer.class), "server" + i));
        }
    }
    */
}
