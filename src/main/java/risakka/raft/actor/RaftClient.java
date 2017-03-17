package risakka.raft.actor;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import lombok.Getter;
import lombok.Setter;
import risakka.raft.message.rpc.client.ClientRequest;
import risakka.raft.message.ClientMessage;
import risakka.raft.log.StateMachineCommand;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


@Getter
@Setter
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
        if (message instanceof ClientMessage) {
            System.out.println(getSelf().path().toSerializationFormat() + " has received " + message.getClass().getSimpleName());
            ((ClientMessage) message).onReceivedBy(this);
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
}
