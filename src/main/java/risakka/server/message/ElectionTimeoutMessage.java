package risakka.server.message;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import risakka.server.actor.RaftServer;
import risakka.server.raft.State;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ElectionTimeoutMessage implements Serializable {

    public ElectionTimeoutMessage() {

    }

    // TODO remove
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("routers-creation-test");
        final int serverNumber = 3;
        List<ActorRef> serverList = new ArrayList<>(serverNumber);
        for (int i = 0; i < serverNumber; i++) {
            serverList.add(system.actorOf(Props.create(RaftServer.class), "server" + i));
        }
    }
}
