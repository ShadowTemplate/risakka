package risakka;

import akka.actor.UntypedActor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FooNode extends UntypedActor {

    public FooNode() {
        System.out.println("Called constructor: " + getSelf().path().toSerializationFormat());
    }

    @Override
    public void onReceive(Object message) throws Throwable {
        System.out.println("Received message: " + message.getClass().getSimpleName());
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        System.out.println("Called preStart: " + getSelf().path().toSerializationFormat());
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        System.out.println("Called postStop: " + getSelf().path().toSerializationFormat());
    }
}
