package risakka.server.actor;

import akka.actor.UntypedActor;
import akka.routing.Router;

public class DemoServer extends UntypedActor {

    private Router broadcastRouter;

    @Override
    public void onReceive(Object message) throws Throwable {
        System.out.println("Received" + message);
    }
}
