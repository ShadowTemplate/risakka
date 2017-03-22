package risakka.raft.miscellanea;

import akka.actor.ActorRef;
import akka.persistence.UntypedPersistentActor;
import akka.routing.Routee;
import akka.routing.Router;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import risakka.raft.log.LogEntry;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor // TODO Maybe we can set (access = AccessLevel.PRIVATE)
public class PersistentState implements Serializable {

    // TODO check how to init them (with 0 or by loading from the persistent state)
    private Integer currentTerm = 0; // a
    private ActorRef votedFor = null;
    private SequentialContainer<LogEntry> log = new SequentialContainer<>();  // first index is 1
    private List<ActorRef> actorsRefs = new ArrayList<ActorRef>();

    public PersistentState copy() {
        return new PersistentState(this.currentTerm, this.votedFor, this.log, this.actorsRefs);
    }

    public void updateCurrentTerm(UntypedPersistentActor owner, Integer currentTerm) {
        this.currentTerm = currentTerm;
        this.votedFor = null;
        owner.saveSnapshot(this.copy());
    }

    public void updateVotedFor(UntypedPersistentActor owner, ActorRef votedFor) {
        this.votedFor = votedFor;
        owner.saveSnapshot(this.copy());
    }

    public void updateLog(UntypedPersistentActor owner, int i, LogEntry item) {
        log.set(i, item);
        owner.saveSnapshot(this.copy());
    }

    public void deleteLogFrom(UntypedPersistentActor owner, int i) {
        log.deleteFrom(i);
        owner.saveSnapshot(this.copy());
    }

    public void updateClusterInfo(UntypedPersistentActor owner, List<ActorRef> actorsRefs) {
        this.actorsRefs = actorsRefs;
        owner.saveSnapshot(this.copy());
    }
    @Override
    public String toString() {
        return "Current State: current term " + this.currentTerm + "; Voted for: " + this.votedFor + ";\n" +
                "Log entries: " + this.log;
    }
}
