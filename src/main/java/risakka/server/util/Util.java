package risakka.server.util;

import akka.actor.*;
import akka.routing.ActorRefRoutee;
import akka.routing.BroadcastRoutingLogic;
import akka.routing.Routee;
import akka.routing.Router;
import risakka.server.actor.DemoServer;
import risakka.server.actor.RaftServer;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toMap;

public class Util {

    public static int getElectionTimeout() {
        return Conf.HEARTBEAT_FREQUENCY * 10 + (new Random().nextInt(Conf.HEARTBEAT_FREQUENCY / 2));
        // TODO check the math. Election timeouts should be chosen randomly from a fixed interval (150-300 ms)
    }

    private static Map<ActorPath, Router> buildBroadcastRoutersMap(List<ActorRef> actors) {
        Map<ActorPath, Router> routerMap = new HashMap<>(actors.size());
        Map<ActorPath, Routee> routeeList = actors.parallelStream().collect(toMap(ActorRef::path, ActorRefRoutee::new));
        for (ActorRef actor : actors) {
            Map<ActorPath, Routee> others = new HashMap<>(routeeList);
            others.remove(actor.path());
            routerMap.put(actor.path(), new Router(new BroadcastRoutingLogic(), others.values()));
        }
        return routerMap;
    }

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

    /*
    // TODO remove
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
