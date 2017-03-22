package risakka.raft.miscellanea;

import akka.actor.ActorRef;
import akka.persistence.UntypedPersistentActor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import risakka.raft.log.LogEntry;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor // TODO Maybe we can set (access = AccessLevel.PRIVATE)
public class PersistentState implements Serializable {

    // TODO check how to init them (with 0 or by loading from the persistent state)
    private Integer currentTerm = 0; // a
    private ActorRef votedFor = null;
    private SequentialContainer<LogEntry> log = new SequentialContainer<>();  // first index is 1

    public PersistentState copy() {
        return new PersistentState(this.currentTerm, this.votedFor, this.log);
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

    @Override
    public String toString() {
        return "Current State: current term " + this.currentTerm + "; Voted for: " + this.votedFor + ";\n" +
                "Log entries: " + this.log;
    }
}
