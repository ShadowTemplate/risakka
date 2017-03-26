package risakka.cluster;

import akka.persistence.Recovery;
import akka.persistence.SnapshotOffer;
import akka.persistence.UntypedPersistentActor;
import lombok.Getter;
import lombok.Setter;
import risakka.raft.log.StateMachineCommand;
import risakka.raft.miscellanea.PersistentState;
import risakka.raft.log.LogEntry;
import risakka.util.conf.server.ServerConf;
import risakka.util.conf.server.ServerConfImpl;

@Getter
@Setter
public class FooNode extends UntypedPersistentActor {


    private PersistentState state;
    private int myId;
    private final ServerConfImpl serverConf;

    public FooNode(int myId) {
        this.myId = myId;
        serverConf = ServerConf.SettingsProvider.get(getContext().system());
        System.out.println("[Foo NODE !! Node " + myId + "]Called constructor: " + getSelf().path().name());
    }

    @Override
    public void onReceiveCommand(Object message) {

        if (state == null) { //Creating a empty state
            LogEntry entry = new LogEntry(2, new StateMachineCommand("aa", 1, 1));
            state = new PersistentState(1, getSender(), null, null);
        }
        if (message instanceof String) {
            //saveSnapshot(new PersistentState(state));
            System.out.println("Received message: " + (String) message + " from " + getSender());
        }


    }

    public String persistenceId() {
        return "id_" + myId;
    }

    @Override
    public void preStart() throws Exception {

        System.out.println("Called preStart: " + getSelf().path().name());

        for (int i = 0; i < serverConf.NODES_PORTS.length; i++) {

            if (myId != i) {
                String address = "akka.tcp://" + serverConf.CLUSTER_NAME + "@" + serverConf.NODES_IPS[i] + ":" +
                        serverConf.NODES_PORTS[i] + "/user/node";
                System.out.println("Sending message to: " + address);
                getContext().actorSelection(address).tell("Hi I'm " + myId, getSelf());
            }


        }

        Recovery.create();
    }

    @Override
    public void postStop() {
        super.postStop();
        System.out.println("Called postStop: " + getSelf().path().name());
    }

    @Override
    public void onReceiveRecover(Object message) {
        System.out.println(getSelf().toString() + "- Recovered!");

        if (message instanceof String) //Called when a message has not been replied yet;
        {                              //In this example, actors send String messages

            String m = ((String) message);

            System.out.println("This was a message not yet replied --> " + m);

        } else if (message instanceof SnapshotOffer) { //Called when an Actor recovers from durable storage
            PersistentState s = (PersistentState) ((SnapshotOffer) message).snapshot();
            System.out.println("Recovering from durable state = " + s);
            state = s;
        } else {
            System.out.println(message + message.getClass().toString());

        }


    }
}
