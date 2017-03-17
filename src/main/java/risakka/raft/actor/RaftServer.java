package risakka.raft.actor;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import akka.routing.Router;
import lombok.Getter;
import lombok.Setter;
import risakka.raft.log.LogEntry;
import risakka.raft.log.StateMachineCommand;
import risakka.raft.message.MessageToServer;
import risakka.raft.message.akka.ElectionTimeoutMessage;
import risakka.raft.message.akka.SendHeartbeatMessage;
import risakka.raft.miscellanea.PersistentState;
import risakka.raft.miscellanea.State;
import risakka.raft.message.rpc.server.AppendEntriesRequest;
import risakka.raft.message.rpc.server.RequestVoteRequest;
import risakka.util.Conf;
import risakka.util.Util;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
public class RaftServer extends UntypedActor {

    // Raft paper fields
    private PersistentState persistentState;

    // volatile fields
    private Integer commitIndex;
    private Integer lastApplied;

    // leader volatile fields // TODO REINITIALIZE AFTER ELECTION
    private Integer[] nextIndex;
    private Integer[] matchIndex;


    // Raft other fields

    // volatile TODO is this right?
    private State state; // FOLLOWER / CANDIDATE / LEADER
    private Set<String> votersIds;
    private ActorRef leaderId;

    // Akka fields

    // volatile fields
    private Router broadcastRouter;
    private Cancellable heartbeatSchedule;
    private Cancellable electionSchedule;


    public RaftServer() {
        System.out.println("Creating RaftServer");
        votersIds = new HashSet<>();
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        toFollowerState();
    }

    // TODO Promemoria: rischedulare immediatamente HeartbeatTimeout appena si ricevono notizie dal server.

    public void onReceive(Object message) throws Throwable {
        if (message instanceof MessageToServer) {
            System.out.println(getSelf().path().toSerializationFormat() + " has received " + message.getClass().getSimpleName());
            ((MessageToServer) message).onReceivedBy(this);
        } else {
            System.out.println("Unknown message type: " + message.getClass());
            unhandled(message);
        }
    }

    public void toFollowerState() {
        state = State.FOLLOWER;
        cancelSchedule(heartbeatSchedule); // Required when state changed from LEADER to FOLLOWER
        scheduleElection();
    }

    public void toCandidateState() {
        state = State.CANDIDATE; // c
        onConversionToCandidate(); // e
    }

    public void toLeaderState() {
        state = State.LEADER;
        leaderId = getSelf();
        cancelSchedule(electionSchedule);
        startHeartbeating();

        // Reinitialize volatile state after election
        for (int i = 0; i < nextIndex.length; i++) { // B
            nextIndex[i] = persistentState.getLog().size() + 1;
            matchIndex[i] = 0;
        }
    }

    private void startHeartbeating() {
        cancelSchedule(heartbeatSchedule);
        // Schedule a new heartbeat for itself. Starts immediately and repeats every HEARTBEAT_FREQUENCY
        heartbeatSchedule = getContext().system().scheduler().schedule(
                Duration.Zero(), // q
                // Duration.create(0, TimeUnit.MILLISECONDS), // q
                Duration.create(Conf.HEARTBEAT_FREQUENCY, TimeUnit.MILLISECONDS), getSelf(), new SendHeartbeatMessage(), // q
                getContext().system().dispatcher(), getSelf());
    }

    private void scheduleElection() {  // TODO remember to reschedule on appendEntry received (call again the method)
        // TODO check if, in addition, ElectionTimeoutMessage in Inbox should be removed
        cancelSchedule(electionSchedule);
        // Schedule a new election for itself. Starts after ELECTION_TIMEOUT and repeats every ELECTION_TIMEOUT
        int electionTimeout = Util.getElectionTimeout(); // p
        electionSchedule = getContext().system().scheduler().scheduleOnce(
                Duration.create(electionTimeout, TimeUnit.MILLISECONDS), getSelf(), new ElectionTimeoutMessage(),
                getContext().system().dispatcher(), getSelf());
    }

    private void cancelSchedule(Cancellable schedule) {
        if (schedule != null && !schedule.isCancelled()) {
            schedule.cancel();
        }
    }

    private void onConversionToCandidate() {  // e
        beginElection(); // d
    }

    public void beginElection() { // d
        votersIds.clear();
        persistentState.updateCurrentTerm(persistentState.getCurrentTerm() + 1); // b
        leaderId = null;
        getPersistentState().updateVotedFor(getSelf());
        votersIds.add(getSelf().path().toSerializationFormat()); // f
        // TODO change randomly my electionTimeout
        scheduleElection(); // g
        System.out.println(getSelf().path().name() + " will broadcast RequestVoteRequest");
        broadcastRouter.route(new RequestVoteRequest(persistentState.getCurrentTerm(), 0, 0), getSelf()); // h // TODO properly set last 2 params
    }

    // TODO move the following methods in an appropriate location

    public void addEntryToLogAndSendToFollowers(StateMachineCommand command) { //u
        LogEntry entry = new LogEntry(command, persistentState.getCurrentTerm());
        int lastIndex = persistentState.getLog().size();
        persistentState.updateLog(lastIndex + 1, entry);
        
        sendAppendEntriesToAllFollowers(); //w
    }

    public void sendAppendEntriesToAllFollowers() { //w
        for (int i = 0; i < nextIndex.length; i++) {
            sendAppendEntriesToOneFollower(this, i);
        }
    }

    public void sendAppendEntriesToOneFollower(RaftServer server, Integer followerId) { //w
        int lastIndex = server.getPersistentState().getLog().size();
        if (lastIndex >= nextIndex[followerId]) {
            //previous entry w.r.t. the new ones that has to match in order to accept the new ones
            LogEntry prevEntry = server.getPersistentState().getLog().get(nextIndex[followerId] - 1);
            Integer prevLogTerm = prevEntry.getTermNumber();

            //get new entries
            List<LogEntry> entries = new ArrayList<>();
            for (int j = nextIndex[followerId]; j <= lastIndex; j++) {
                entries.add(server.getPersistentState().getLog().get(j));
            }
            //TODO send request to follower i
            new AppendEntriesRequest(server.getPersistentState().getCurrentTerm(), nextIndex[followerId] - 1, prevLogTerm, entries, server.getCommitIndex());
        }
    }
    
    public void checkEntriesToCommit() { // z //call iff leader
        for (int i = persistentState.getLog().size(); i > commitIndex; i--) {
            int count = 1; // on how many server the entry is replicated (myself for sure)

            for (Integer index : matchIndex) {
                if (index >= i && persistentState.getLog().get(i).getTermNumber().equals(persistentState.getCurrentTerm())) {
                    count++;
                }
            }

            if (count > Conf.SERVER_NUMBER / 2) {
                commitIndex = i;
                break;
            }
        }
    }
}
