package risakka.server.raft;

import akka.actor.ActorRef;
import lombok.AllArgsConstructor;
import lombok.Getter;
import risakka.server.persistence.Durable;
import risakka.server.persistence.PersistenceManager;
import risakka.server.persistence.StorageException;

@Getter
@AllArgsConstructor
public class PersistentState implements Durable {
    // persistent fields  // TODO These 3 fields must be updated on stable storage before responding to RPC
    private Integer currentTerm = 0; // a // TODO check if init with 0 or by loading from the persistent state
    private ActorRef votedFor;  // TODO reset to null after on currentTerm change?
    private SequentialContainer<LogEntry> log;  // first index is 1

    // TODO fix getLog() bypassing update

    public void updateCurrentTerm(Integer currentTerm) {
        this.currentTerm = currentTerm;
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
}
