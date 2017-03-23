package risakka.raft.miscellanea;

import akka.actor.ActorRef;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import risakka.raft.actor.RaftServer;
import risakka.raft.log.LogEntry;
import risakka.util.Util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

@ToString
@Getter
@NoArgsConstructor
@AllArgsConstructor // TODO Maybe we can set (access = AccessLevel.PRIVATE)
public class PersistentState implements Serializable {

    // TODO check how to init them (with 0 or by loading from the persistent state)
    private Integer currentTerm = 0; // a
    private ActorRef votedFor = null;
    private SequentialContainer<LogEntry> log = new SequentialContainer<>();  // first index is 1
    private Collection<ActorRef> actorsRefs = null;

    private PersistentState(PersistentState persistentState) {
        this.currentTerm = persistentState.currentTerm;
        this.votedFor = persistentState.votedFor;
        this.log = new SequentialContainer<>(persistentState.log);
        this.actorsRefs = new ArrayList<>();
        this.actorsRefs.addAll(persistentState.actorsRefs);
    }

    public void updateCurrentTerm(RaftServer raftServer, Integer currentTerm) {
        this.currentTerm = currentTerm;
        this.votedFor = null;
        raftServer.saveSnapshot(new PersistentState(this));
    }

    public void updateVotedFor(RaftServer raftServer, ActorRef votedFor) {
        this.votedFor = votedFor;
        raftServer.saveSnapshot(new PersistentState(this));
    }

    public void updateLog(RaftServer raftServer, int i, LogEntry item) {
        log.set(i, item);
        raftServer.saveSnapshot(new PersistentState(this));
    }

    public void deleteLogFrom(RaftServer raftServer, int i) {
        log.deleteFrom(i);
        raftServer.saveSnapshot(new PersistentState(this));
    }

    public void updateActorRefs(RaftServer raftServer, Collection<ActorRef> actorsRefs) {
        this.actorsRefs = actorsRefs;
        recreateBroadcastRouter(raftServer);
        raftServer.saveSnapshot(new PersistentState(this));
    }

    public void recreateBroadcastRouter(RaftServer raftServer) {
        raftServer.setBroadcastRouter(Util.buildBroadcastRouter(raftServer.getSelf(), actorsRefs));
    }
}
