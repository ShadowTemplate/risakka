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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import risakka.raft.log.StateMachineCommand;
import risakka.raft.message.MessageToClient;
import risakka.raft.message.rpc.client.ClientRequest;
import risakka.raft.message.rpc.client.RegisterClientRequest;
import risakka.util.Util;
import risakka.util.conf.client.ClientConf;
import risakka.util.conf.client.ClientConfImpl;

import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;


@Getter
@Setter
public class RaftClient extends UntypedActor {

    private ActorRef client;
    private int clientId;
    private Integer seqNumber;
    private ActorSelection serverAddress;


    private Timeout answeringTimeout;
    private final ClientConfImpl clientConf;

    public RaftClient() {
        System.out.println("Creating RaftClient");
        clientConf = ClientConf.SettingsProvider.get(getContext().system());
        this.client = getSelf();
        this.seqNumber = 0;
        this.answeringTimeout = new Timeout(Duration.create(clientConf.ANSWERING_TIMEOUT, TimeUnit.MILLISECONDS));
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
        int serverToContact = (int) (Math.random() * (clientConf.SERVER_NUMBER));
        System.out.println("Server chosen randomly: " + serverToContact);
        return serverToContact;
    }

    public ActorSelection buildAddressFromId(int id) {
        String address = Util.getAddressFromId(id, clientConf.SERVER_CLUSTER_NAME, clientConf.NODES_IPS[id], clientConf.NODES_PORTS[id], clientConf.SERVER_PREFIX_NODE_NAME);
        return getContext().actorSelection(address);
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
            if (attempts < clientConf.MAX_ATTEMPTS) { //try again until MAX_ATTEMPTS is reached
                registerContactingRandomServer(attempts + 1);
            } else { //stop
                System.out.println("Crashing: no successful registration");
                getContext().stop(getSelf());
                //TODO crash
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
            if (attempts < clientConf.MAX_ATTEMPTS) { //try again until MAX_ATTEMPTS is reached
                System.out.println("Server didn't reply. Choosing another server");
                serverAddress = buildAddressFromId(getRandomServerId());
                sendClientRequest(message, attempts + 1);
            } else { //stop
                System.out.println("Crashing: no successful response");
                getContext().stop(getSelf());
                //TODO crash
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
        ClientConfImpl conf = new ClientConfImpl(config);
        conf.printConfiguration();

        ActorSystem system = ActorSystem.create(conf.CLIENT_CLUSTER_NAME, config);
        system.actorOf(Props.create(RaftClient.class), conf.CLIENT_NODE_NAME);


    }
}
