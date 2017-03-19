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
    private Integer seqNumber;
    private ActorSelection serverAddress;
    
    
    private Timeout answeringTimeout; 
    private int MAX_ATTEMPTS;

    public RaftClient() {
        System.out.println("Creating RaftClient");
        this.client = getSelf();
        this.seqNumber = 0;
        scheduleRequest();
        this.MAX_ATTEMPTS = Conf.SERVER_NUMBER; //TODO set an appropriate number
        this.answeringTimeout = new Timeout(Duration.create(1000, TimeUnit.MILLISECONDS));  //TODO set an appropriate timeout
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();        
        registerContactingRandomServer();
    }
    
    @Override
    public void onReceive(Object message) throws Throwable {
        //client should not be contacted by server first
        
        //just to automatically send new client requests
        if (message instanceof ClientRequest) {
            System.out.println(getSelf().path().toSerializationFormat() + " sends client request with id " + ((ClientRequest)message).getCommand().getSeqNumber());
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
        getContext().system().scheduler().schedule(Duration.Zero(),
                Duration.create(400, TimeUnit.MILLISECONDS), client,
                new ClientRequest(new StateMachineCommand("command" + seqNumber, 0, seqNumber)), 
                getContext().system().dispatcher(), getSelf());
        seqNumber++; // TODO DOESN'T INCREMENT EVERY TIME -> the problem is that it's scheduled once and it's not performed each time
    }
    
    // TODO move the following methods in an appropriate location
    
    public void registerContactingRandomServer() {
        registerContactingRandomServer(1);
    }
    
    private void registerContactingRandomServer(int attempts) {
        //contact a random server
        registerContactingSpecificServer(getRandomServerId(), attempts);
    }
    
    private int getRandomServerId() {
        int serverToContact = (int) (Math.random() * (Conf.SERVER_NUMBER));
        System.out.println("Server chosen randomly: " + serverToContact);
        return serverToContact;
    }
    
    private ActorSelection buildAddressFromId(int id) {
        //TODO split server and client conf
        return getContext().actorSelection("akka.tcp://" + Conf.CLUSTER_NAME + "@" + Conf.NODES_IPS[id] + ":"
                + Conf.NODES_PORTS[id] + "/user/node_" + id);
    }
    
    public void registerContactingSpecificServer(int serverId) {
        registerContactingSpecificServer(serverId, 1);
    }
    
    private void registerContactingSpecificServer(int serverId, int attempts) {
        System.out.println("Contacting server " + serverId + " - attempt " + attempts);
        serverAddress = buildAddressFromId(serverId);
        //send register request and wait
        Future<Object> future = Patterns.ask(serverAddress, new RegisterClientRequest(), answeringTimeout);

        try {
            processResponse(Await.result(future, answeringTimeout.duration()));

        } catch (Exception ex) {
            Logger.getLogger(RaftClient.class.getName()).log(Level.SEVERE, null, ex);

            if (attempts < MAX_ATTEMPTS) { //try again until MAX_ATTEMPTS is reached
                registerContactingRandomServer(attempts + 1);
            } else { //stop
                System.out.println("Crashing: no successful registration");
                getContext().stop(getSelf());
            }

        }
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
