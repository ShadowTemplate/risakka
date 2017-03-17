package risakka;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.persistence.Recovery;
import akka.persistence.SnapshotOffer;
import akka.persistence.UntypedPersistentActor;
import lombok.Getter;
import lombok.Setter;
import risakka.server.persistence.Durable;
import risakka.server.raft.LogEntry;
import risakka.server.raft.SequentialContainer;
import risakka.server.util.Conf;

@Getter
@Setter
public class FooNode extends UntypedPersistentActor {

    private class PersistentState implements Durable {
        // persistent fields  // TODO These 3 fields must be updated on stable storage before responding to RPC
        private Integer currentTerm = 0; // a // TODO check if init with 0 or by loading from the persistent state
        private ActorRef votedFor;  // TODO reset to null after on currentTerm change?
        private SequentialContainer<LogEntry> log;  // first index is 1

        // TODO SETTER WITH UPDATE ON PERS STORAGE
    }

    private PersistentState state;
    private int myId;

    public FooNode(int myId) {
        this.myId= myId;
        System.out.println("[Node "+ myId+"]Called constructor: " + getSelf().path().toSerializationFormat());
    }

    @Override
    public void onReceiveCommand(Object message){
        System.out.println("Received message: " + message.getClass().getSimpleName());
    }

    public String persistenceId() { return "id_"; }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        System.out.println("Called preStart: " + getSelf().path().toSerializationFormat());

        for (int i=0; i< Conf.NODES_PORTS.length; i++ ) {

            if(myId != i)
            {
                String address = "akka.tcp://raft-cluster@"+Conf.NODES_IPS[i]+":"+
                        Conf.NODES_PORTS[i]+"/user/node";
                System.out.println("Sending message to: "+ address);
                getContext().actorSelection(address).tell("Hi I'm "+ myId, getSelf());
            }


        }

        Recovery.create();
    }

    @Override
    public void postStop() {
        super.postStop();
        System.out.println("Called postStop: " + getSelf().path().toSerializationFormat());
    }

    @Override
    public void onReceiveRecover(Object message) {
        System.out.println(getSelf().toString()+"- Recovered!");

        //System.out.println(this.state.getContent().toString());
        if(message instanceof PersistentState)
        {

            PersistentState m = ((PersistentState) message);

            //System.out.println(m);

        }
        else if (message instanceof SnapshotOffer) {
            PersistentState s = (PersistentState) ((SnapshotOffer) message).snapshot();
            System.out.println("Recovering from durable state = " + s);
            state = s;
        }
        else{
            System.out.println(message+ message.getClass().toString());

        }


    }
}
