package risakka.raft.miscellanea;

import akka.actor.ActorRef;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import risakka.raft.log.LogEntry;
import risakka.persistence.Durable;
import risakka.persistence.PersistenceManager;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PersistentState implements Durable {

    // persistent fields
    // TODO check how to init them (with 0 or by loading from the persistent state)
    private Integer currentTerm = 0; // a
    private ActorRef votedFor = null;
    private SequentialContainer<LogEntry> log = new SequentialContainer<>();  // first index is 1

    public void updateCurrentTerm(Integer currentTerm) {
        this.currentTerm = currentTerm;
        this.votedFor = null;
        PersistenceManager.instance.persist(this);
    }

    public void updateVotedFor(ActorRef votedFor) {
        this.votedFor = votedFor;
        PersistenceManager.instance.persist(this);
    }

    public void updateLog(int i, LogEntry item) {
        log.set(i, item);
        PersistenceManager.instance.persist(this);
    }

    public void deleteLogFrom(int i) {
        log.deleteFrom(i);
        PersistenceManager.instance.persist(this);
    }

    public PersistentState copy(){ return new PersistentState(this.currentTerm, this.votedFor, this.log);}
}
