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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import lombok.Getter;
import lombok.Setter;
import risakka.raft.message.rpc.client.ClientRequest;
import risakka.raft.message.MessageToClient;
import risakka.raft.log.StateMachineCommand;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import lombok.AllArgsConstructor;
import risakka.raft.message.rpc.client.RegisterClientRequest;
import risakka.util.Conf;
import scala.concurrent.Await;
import scala.concurrent.Future;


@Getter
@Setter
public class RaftClient extends UntypedActor {

    private ActorRef client;
    private int clientId;
    private Integer seqNumber;
    private ActorSelection serverAddress;


    private Timeout answeringTimeout;
    private int MAX_ATTEMPTS;

    public RaftClient() {
        System.out.println("Creating RaftClient");
        this.client = getSelf();
        this.seqNumber = 0;
        this.MAX_ATTEMPTS = Conf.SERVER_NUMBER; //TODO set an appropriate number
        this.answeringTimeout = new Timeout(Duration.create(1000, TimeUnit.MILLISECONDS));  //TODO set an appropriate timeout
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        ConsoleReader reader = new ConsoleReader(this);
        Thread t = new Thread(reader);
        t.start();
        registerContactingRandomServer();
    }

    @Override
    public void onReceive(Object message) throws Throwable {
        //client should not be contacted by server first

        if (message instanceof ClientRequest) {
            sendClientRequest((ClientRequest)message);
        } else {
            System.out.println("Unknown message type: " + message.getClass());
            unhandled(message);
        }
    }

    //process responses of the server
    public void processResponse(Object responseMessage) {
        processResponse(null, responseMessage);
    }
    
    //process responses of the server based on its request
    public void processResponse(Object requestMessage, Object responseMessage) {
        if (responseMessage instanceof MessageToClient) {
            System.out.println(getSelf().path().name() + " has received " + responseMessage.getClass().getSimpleName());
            ((MessageToClient) responseMessage).onReceivedBy(this, requestMessage);
        } else {
            System.out.println("Unknown message type: " + responseMessage.getClass());
            System.out.println("process");
            unhandled(responseMessage);
        }
    }

    // TODO move the following methods in an appropriate location

    public int getRandomServerId() {
        int serverToContact = (int) (Math.random() * (Conf.SERVER_NUMBER));
        System.out.println("Server chosen randomly: " + serverToContact);
        return serverToContact;
    }

    public ActorSelection buildAddressFromId(int id) {
        //TODO split server and client conf
        return getContext().actorSelection("akka.tcp://" + Conf.CLUSTER_NAME + "@" + Conf.NODES_IPS[id] + ":"
                + Conf.NODES_PORTS[id] + "/user/node_" + id);
    }
    
    public void registerContactingRandomServer() {
        registerContactingRandomServer(1);
    }

    private void registerContactingRandomServer(int attempts) {
        //contact a random server
        registerContactingSpecificServer(getRandomServerId(), attempts);
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
            if (attempts < MAX_ATTEMPTS) { //try again until MAX_ATTEMPTS is reached
                registerContactingRandomServer(attempts + 1);
            } else { //stop
                System.out.println("Crashing: no successful registration");
                getContext().stop(getSelf());
            }
        }
    }
    
    public void sendClientRequest(ClientRequest message) {
        sendClientRequest(message, 1);
    }
    
    private void sendClientRequest(ClientRequest message, int attempts) {
        System.out.println(getSelf().path().name() + " sends client request with id " + message.getCommand().getSeqNumber() + " - attempt " + attempts);

        Future<Object> future = Patterns.ask(serverAddress, message, answeringTimeout);
        try {
            processResponse(message, Await.result(future, answeringTimeout.duration()));

        } catch (TimeoutException ex) {
            if (attempts < MAX_ATTEMPTS) { //try again until MAX_ATTEMPTS is reached
                System.out.println("Server didn't reply. Choosing another server");
                serverAddress = buildAddressFromId(getRandomServerId());
                sendClientRequest(message, attempts + 1);
            } else { //stop
                System.out.println("Crashing: no successful response");
                getContext().stop(getSelf());
            }
        } catch (Exception ex) {
            Logger.getLogger(RaftClient.class.getName()).log(Level.SEVERE, null, ex);
        }
 
    }

    @AllArgsConstructor
    class ConsoleReader implements Runnable {

        private RaftClient client;

        @Override
        public void run() {
            while (true) {
                try {
                    BufferedReader bufferRead = new BufferedReader(new InputStreamReader(System.in));
                    System.out.println("Type your message to be sent to the server: ");
                    String command = bufferRead.readLine();

                    System.out.println("New command: " + command + " - SeqNumber: " + client.seqNumber);
                    client.onReceive(new ClientRequest(new StateMachineCommand(command, client.clientId, client.seqNumber++)));
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Throwable ex) {
                    Logger.getLogger(RaftClient.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }


    public static void main(String[] args) {


        Config config = ConfigFactory.load("client_configuration");

        ActorSystem system = ActorSystem.create(Conf.CLUSTER_NAME, config);
        system.actorOf(Props.create(RaftClient.class), "client");


    }
}
