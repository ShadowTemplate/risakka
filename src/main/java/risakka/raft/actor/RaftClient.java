package risakka.raft.actor;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.Getter;
import lombok.Setter;
import risakka.raft.message.rpc.client.ClientRequest;
import risakka.raft.message.MessageToClient;
import risakka.raft.log.StateMachineCommand;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import risakka.raft.message.rpc.client.RegisterClientRequest;
import risakka.util.Conf;
import scala.concurrent.Await;
import scala.concurrent.Future;


@Getter
@Setter
public class RaftClient extends UntypedActor {

    private ActorRef client;
    private static AtomicInteger seqNumber = new AtomicInteger(0);
    private ActorSelection serverAddress;
    
    
    private Timeout answeringTimeout; 
    private int MAX_ATTEMPTS;

    public RaftClient() {
        System.out.println("Creating RaftClient");
        this.client = getSelf();
        scheduleRequest();
        MAX_ATTEMPTS = Conf.SERVER_NUMBER; //TODO set an appropriate number
        this.answeringTimeout = new Timeout(Duration.create(1000, TimeUnit.MILLISECONDS));  //TODO set an appropriate timeout
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        registerContactingRandomServer(1);
    }
    
    @Override
    public void onReceive(Object message) throws Throwable {
        //client should not be contacted by server first
        
        //just to automatically send new client requests
        if (message instanceof ClientRequest) {
            System.out.println(getSelf().path().toSerializationFormat() + " sends client request with id " + ((ClientRequest)message).getRequestId());
            serverAddress.tell(message, client);
            
        } else {
            System.out.println("Unknown message type: " + message.getClass());
            unhandled(message);
        }
    }

    //process responses of the server
    public void processResponse(Object message) {
        if (message instanceof MessageToClient) {
            System.out.println(getSelf().path().toSerializationFormat() + " has received " + message.getClass().getSimpleName());
            ((MessageToClient) message).onReceivedBy(this);
        } else {
            System.out.println("Unknown message type: " + message.getClass());
            unhandled(message);
        }
    }

    private void scheduleRequest() {
        int requestNum = seqNumber.getAndIncrement();
        getContext().system().scheduler().schedule(Duration.Zero(),
                Duration.create(400, TimeUnit.MILLISECONDS), client,
                new ClientRequest(requestNum, new StateMachineCommand("command" + requestNum)), // TODO DOESN'T INCREMENT EVERY TIME -> the problem is that it's scheduled once and it's not performed each time
                getContext().system().dispatcher(), getSelf());
    }
    
    // TODO move the following methods in an appropriate location
    
    public void registerContactingRandomServer(int attempts) {
        //contact a random server
        System.out.println("Register contacting a random server - attempt " + attempts);
        serverAddress = getRandomServerAddress();
        
        //send register request and wait
        Future<Object> future = Patterns.ask(serverAddress, new RegisterClientRequest(), answeringTimeout);

        try {
            processResponse(Await.result(future, answeringTimeout.duration()));
 
        } catch (Exception ex) {
            Logger.getLogger(RaftClient.class.getName()).log(Level.SEVERE, null, ex);
            
            if(attempts < MAX_ATTEMPTS) { //try again until MAX_ATTEMPTS is reached
                registerContactingRandomServer(attempts + 1);
            } else { //stop
                System.out.println("Crashing: no successful registration");
                getContext().stop(getSelf());
            }
            
        }
        
        
    }
    
    public ActorSelection getRandomServerAddress() {
        int serverToContact = (int) (Math.random() * (Conf.SERVER_NUMBER));
        System.out.println("Server to contact " + serverToContact);

        //TODO split server and client conf
        String address = "akka.tcp://" + Conf.CLUSTER_NAME + "@" + Conf.NODES_IPS[serverToContact] + ":"
                + Conf.NODES_PORTS[serverToContact] + "/user/node";
        return getContext().actorSelection(address);
    }
    
    public static void main(String[] args) {
        
        String c = new String("akka.actor.provider=remote \n"
                + "akka.remote.netty.tcp.hostname=\"" + "127.0.0.1" + "\"\n"
                + "akka.remote.netty.tcp.port=" + "20000" + "\n"
                + "akka.remote.enabled-transports=[\"akka.remote.netty.tcp\"] \n");

        Config config = ConfigFactory.parseString(c);

        ActorSystem system = ActorSystem.create(Conf.CLUSTER_NAME, config);
        system.actorOf(Props.create(RaftClient.class), "client");
        
                
    }
}
