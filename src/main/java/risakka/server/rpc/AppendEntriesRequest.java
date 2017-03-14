package risakka.server.rpc;

import lombok.AllArgsConstructor;
import lombok.Data;
import risakka.server.raft.LogEntry;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
public class AppendEntriesRequest implements Serializable {

    private Integer term;
    // leaderId can be retrieved via Akka's getSender() method
    private Integer prevLogIndex;
    private Integer prevLogTerm;
    private List<LogEntry> entries;
    private Integer leaderCommit;

}
