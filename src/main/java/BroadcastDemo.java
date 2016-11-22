import akka.actor.*;
import akka.routing.ActorRefRoutee;
import akka.routing.BroadcastRoutingLogic;
import akka.routing.Routee;
import akka.routing.Router;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class BroadcastDemo {

    private static class BroadcastMessage implements Serializable {
        BroadcastMessage() { }
    }

    private static class Leader extends UntypedActor {
        private Router router;

        public Leader(Router router) {
            this.router = router;
        }

        public void preStart() {
            router.route(new BroadcastMessage(), getSelf());
        }

        public void onReceive(Object message) {
            System.out.println("WTF, why did I receive a message? I'm the leader!");
            unhandled(message);
        }
    }

    private static class Follower extends UntypedActor {

        public Follower() {}

        public void onReceive(Object message) {
            if (message instanceof BroadcastMessage) {
                System.out.println("Received broadcast message by " + getSelf().path().name());
            } else {
                System.out.println("Unhandled message: " + message.getClass());
                unhandled(message);
            }
        }
    }

    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("broadcastdemo");
        List<Routee> actorsList = new ArrayList<Routee>();
        for (int i = 0; i < 5; i++) {
            ActorRef follower = system.actorOf(Props.create(Follower.class), "follower" + i);
            actorsList.add(new ActorRefRoutee(follower));
        }
        Router router = new Router(new BroadcastRoutingLogic(), actorsList);
        system.actorOf(Props.create(Leader.class, router), "leader");
    }
}
