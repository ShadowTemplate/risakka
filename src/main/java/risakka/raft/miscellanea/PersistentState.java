package risakka.raft.miscellanea;

import akka.actor.ActorRef;
import lombok.*;
import org.apache.log4j.Logger;
import risakka.raft.actor.RaftServer;
import risakka.raft.log.LogEntry;

import javax.swing.*;
import java.io.Serializable;
import java.util.List;

@ToString
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PersistentState implements Serializable {

    private Integer currentTerm = 0; // a
    private ActorRef votedFor = null;
    private SequentialContainer<LogEntry> log = new SequentialContainer<>();  // first index is 1
    private static final Logger logger = Logger.getLogger(PersistentState.class);

    public void updateCurrentTerm(RaftServer raftServer, Integer currentTerm, Runnable onSuccess) {
        this.currentTerm = currentTerm;
        this.votedFor = null;
        raftServer.persist(this, ignored -> {
            logger.debug("persistent updateCurrentTerm called with success");
            onSuccess.run();
        });
    }

    public void updateVotedFor(RaftServer raftServer, ActorRef votedFor, Runnable onSuccess) {
        this.votedFor = votedFor;
        raftServer.persist(this, ignored -> {
            logger.debug("persistent updateVotedFor called with success");
            onSuccess.run();
        });

    }

    public void updateLog(RaftServer raftServer, int i, LogEntry item, Runnable onSuccess) {
        if (!(raftServer.getState() != ServerState.LEADER || log.size() < i)) {
            JOptionPane.showMessageDialog(null, "Leader Append-Only property violated", "Property violation", JOptionPane.ERROR_MESSAGE);
        }
        assert raftServer.getState() != ServerState.LEADER || log.size() < i : "Leader Append-Only property violated";
        log.set(i, item);
        raftServer.persist(this, ignored -> {
            logger.debug("persistent updateLog called with success");
            onSuccess.run();
        });
    }

    public void updateLog(RaftServer raftServer, int startIndex, List<LogEntry> entries, Runnable onSuccess) {
        int currIndex = startIndex;
        for (LogEntry entry : entries) {
            if (log.size() >= currIndex && // there is already an entry in that position
                    !log.get(currIndex).getTermNumber().equals(entry.getTermNumber())) { // the preexisting entry's term and the new one's are different
                if (!(raftServer.getState() != ServerState.LEADER)) {
                    JOptionPane.showMessageDialog(null, "Leader Append-Only property violated", "Property violation", JOptionPane.ERROR_MESSAGE);
                }
                assert raftServer.getState() != ServerState.LEADER : "Leader Append-Only property violated";
                log.deleteFrom(currIndex);
            }
            if (!(raftServer.getState() != ServerState.LEADER || log.size() < currIndex)) {
                JOptionPane.showMessageDialog(null, "Leader Append-Only property violated", "Property violation", JOptionPane.ERROR_MESSAGE);
            }
            assert raftServer.getState() != ServerState.LEADER || log.size() < currIndex : "Leader Append-Only property violated";
            log.set(currIndex, entry);
            EventNotifier.getInstance().updateLog(raftServer.getId(), currIndex, entry);
            currIndex++;
        }
        raftServer.persist(this, ignored -> {
            logger.debug("updateLog called with success");
            onSuccess.run();
        });

    }

}
