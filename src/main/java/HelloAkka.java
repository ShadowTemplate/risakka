import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Cancellable;
import scala.concurrent.duration.Duration;

public class HelloAkka {
    final static int N_SENDERS = 5;

    public static class Hello implements Serializable {
        public final String msg;

        public Hello(String msg) {
            this.msg = msg;
        }
    }

    // The Receiver actor class
    public static class Receiver extends UntypedActor {
        public void onReceive(Object message) {        // This function is called on every message received by the actor.
            if (message instanceof Hello) {            // Like this you can distinguish the types of received messages
                Hello h = (Hello) message;
                System.out.println("[" + getSelf().path().name() +    // the name of the current actor
                        "] received a message from " + getSender().path().name() + // the name of the sender actor
                        ": " + h.msg                    // finally the message contents
                );
            } else unhandled(message);                // for messages we don't know what to do with
        }
    }

    // The Sender actor class
    public static class Sender extends UntypedActor {
        private ActorRef receiver;

        public Sender(ActorRef receiver) {
            this.receiver = receiver;    // this actor will be the destination of our messages
        }

        public void preStart() {
            // Create a timer that will periodically send a message to the receiver actor
            Cancellable timer = getContext().system().scheduler().schedule(
                    Duration.create(1, TimeUnit.SECONDS),                // when to start generating messages
                    Duration.create(1, TimeUnit.SECONDS),                // how frequently generate them
                    receiver,                                            // destination actor reference
                    new Hello("Hello from " + getSelf().path().name()), // the message to send
                    getContext().system().dispatcher(), getSelf()        // source of the message (myself)
            );
        }

        public void onReceive(Object message) {
            unhandled(message);        // this actor does not handle any incoming messages
        }
    }

    public static void main(String[] args) {
        // Create the 'helloakka' actor system
        final ActorSystem system = ActorSystem.create("helloakka");

        // Create a single receiver actor
        final ActorRef receiver = system.actorOf(
                Props.create(Receiver.class),    // actor class
                "receiver"                        // actor name
        );

        // Create multiple sender actors
        for (int i = 0; i < N_SENDERS; i++) {
            system.actorOf(
                    Props.create(        // actor properties
                            Sender.class,    // actor class
                            receiver),        // the constructor parameters are passed here (the receiver in our case)
                    "sender" + i);        // actor name
        }
    }
}
