import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Cancellable;
import scala.concurrent.duration.Duration;

public class HeartBeatDemo {

    private static class HeartBeatTimeout implements Serializable {
        HeartBeatTimeout() { }
    }

    private static class HeartBeat implements Serializable {
        HeartBeat() { }
    }

    private static class Leader extends UntypedActor {
        private ActorRef follower;
        private final int PING_INTERVAL = 2;

        public Leader(ActorRef follower) {
            this.follower = follower;
        }

        public void preStart() {
            getContext().system().scheduler().schedule(
                    Duration.create(0, TimeUnit.SECONDS), // when to start generating messages
                    Duration.create(PING_INTERVAL, TimeUnit.SECONDS), // how frequently generate them
                    follower, // destination actor reference
                    new HeartBeat(), // the message to send
                    getContext().system().dispatcher(), getSelf()); // source of the message (myself)
        }

        public void onReceive(Object message) {
            System.out.println("WTF, why did I receive a message? I'm the leader!");
            unhandled(message);
        }
    }

    private static class Follower extends UntypedActor {
        private Cancellable currTimeout;
        private final int HEARTBEAT_TIMEOUT = 3;

        public Follower() {}

        public void preStart() {
            currTimeout = scheduleTimeout();
        }

        private Cancellable scheduleTimeout() {
            return getContext().system().scheduler().schedule(
                    Duration.create(HEARTBEAT_TIMEOUT, TimeUnit.SECONDS), // when to start generating messages
                    Duration.create(HEARTBEAT_TIMEOUT, TimeUnit.SECONDS), // how frequently generate them
                    getSelf(), // destination actor reference
                    new HeartBeatTimeout(), // the message to send
                    getContext().system().dispatcher(), getSelf()); // source of the message (myself)
        }

        private void resetHeartbeat() {
            currTimeout.cancel();
            currTimeout = scheduleTimeout();
        }

        public void onReceive(Object message) {
            if (message instanceof HeartBeat) {
                System.out.println("Received heartbeat");
                resetHeartbeat(); // if you comment this line, heart beat timeouts will arrive
            } else if (message instanceof HeartBeatTimeout) {
                System.out.println("Received heartbeat timeout");
            } else {
                System.out.println("Unhandled message");
                unhandled(message);
            }
        }
    }

    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("heartbeatdemo");
        ActorRef follower = system.actorOf(Props.create(Follower.class), "follower");
        system.actorOf(Props.create(Leader.class, follower), "leader");
    }
}
