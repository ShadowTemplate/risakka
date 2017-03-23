package risakka.raft.miscellanea;

import akka.actor.ActorRef;
import akka.persistence.UntypedPersistentActor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import risakka.raft.log.LogEntry;

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

    public PersistentState(PersistentState persistentState) {
        this.currentTerm = persistentState.currentTerm;
        this.votedFor = persistentState.votedFor;
        this.log = new SequentialContainer<>(persistentState.log);
        this.actorsRefs = new ArrayList<>();
        this.actorsRefs.addAll(persistentState.actorsRefs);
    }

    public void updateCurrentTerm(UntypedPersistentActor owner, Integer currentTerm) {
        this.currentTerm = currentTerm;
        this.votedFor = null;
        owner.saveSnapshot(new PersistentState(this));
    }

    public void updateVotedFor(UntypedPersistentActor owner, ActorRef votedFor) {
        this.votedFor = votedFor;
        owner.saveSnapshot(new PersistentState(this));
    }

    public void updateLog(UntypedPersistentActor owner, int i, LogEntry item) {
        log.set(i, item);
        owner.saveSnapshot(new PersistentState(this));
    }

    public void deleteLogFrom(UntypedPersistentActor owner, int i) {
        log.deleteFrom(i);
        owner.saveSnapshot(new PersistentState(this));
    }

    public void updateActorRefs(UntypedPersistentActor owner, Collection<ActorRef> actorsRefs) {
        this.actorsRefs = actorsRefs;
        owner.saveSnapshot(new PersistentState(this));
    }
}
