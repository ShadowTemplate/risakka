package risakka.server.actor;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import risakka.server.message.ClientRequest;
import risakka.server.message.ServerResponse;
import risakka.server.raft.StateMachineCommand;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class RaftClient extends UntypedActor {

    private ActorRef server;
    private static AtomicInteger lastRequestId = new AtomicInteger(0);


    public RaftClient() {
        this.server = getSelf();
        scheduleRequest();
        System.out.println("Creating RaftClient");
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
    }

    @Override
    public void onReceive(Object message) throws Throwable {
        if (message instanceof ServerResponse) {
            ServerResponse response = (ServerResponse) message;
            server = response.getLeader();
            System.out.println("Client " + getSelf().path().toSerializationFormat() + " has received response from server " + server.path().toSerializationFormat() +
                    " for request " + response.getRequestId());
        } else if (message instanceof ClientRequest) {
            ClientRequest request = (ClientRequest) message;
            System.out.println("Server has received request from client " + getSender().path().toSerializationFormat() +
                    " with id " + request.getRequestId());
            getSender().tell(new ServerResponse(request.getRequestId(), getSelf()), getSelf());
        } else {
            System.out.println("Unknown message type: " + message.getClass());
            unhandled(message);
        }
    }

    private void scheduleRequest() {
        getContext().system().scheduler().schedule(Duration.Zero(),
                Duration.create(400, TimeUnit.MILLISECONDS), server,
                new ClientRequest(lastRequestId.getAndIncrement(), new StateMachineCommand("command" + lastRequestId.getAndIncrement())), // TODO DOESN'T INCREMENT EVERY TIME
                getContext().system().dispatcher(), getSelf());
    }

    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("routers-creation-test");
        final int serverNumber = 3;
        List<ActorRef> serverList = new ArrayList<>(serverNumber);
        for (int i = 0; i < serverNumber; i++) {
            serverList.add(system.actorOf(Props.create(RaftClient.class), "server" + i));
        }
    }
}
