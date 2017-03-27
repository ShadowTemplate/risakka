package risakka.raft.miscellanea;

import akka.actor.ActorRef;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import risakka.raft.actor.RaftServer;
import risakka.raft.log.LogEntry;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import risakka.gui.EventNotifier;

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

    public void updateCurrentTerm(RaftServer raftServer, Integer currentTerm, Runnable onSuccess) {
        this.currentTerm = currentTerm;
        this.votedFor = null;
        raftServer.persist(this, ignored -> {
            onSuccess.run();
        });
    }

    public void updateVotedFor(RaftServer raftServer, ActorRef votedFor, Runnable onSuccess) {
        this.votedFor = votedFor;
        raftServer.persist(this, ignored -> {
            onSuccess.run();
        });
    }

    public void updateLog(RaftServer raftServer, int i, LogEntry item, Runnable onSuccess) {
        log.set(i, item);
        raftServer.persist(this, ignored -> {
            onSuccess.run();
        });
    }

    public void updateLog(RaftServer raftServer, int startIndex, List<LogEntry> entries, Runnable onSuccess) {
        int currIndex = startIndex;
        for (LogEntry entry : entries) {
            if (log.size() >= currIndex && // there is already an entry in that position
                    !log.get(currIndex).getTermNumber().equals(entry.getTermNumber())) { // the preexisting entry's term and the new one's are different
                log.deleteFrom(currIndex);
            }
            log.set(currIndex, entry);
            EventNotifier.getInstance().updateLog(raftServer.getId(), currIndex, entry);
            currIndex++;
        }
        raftServer.persist(this, ignored -> {
            onSuccess.run();
        });
    }

    public void updateActorRefs(RaftServer raftServer, Collection<ActorRef> actorsRefs,
                                Runnable onSuccess) {
        this.actorsRefs = actorsRefs;
        raftServer.persist(this, ignored -> {
            onSuccess.run();
        });
    }
}
