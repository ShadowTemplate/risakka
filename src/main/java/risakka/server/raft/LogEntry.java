package risakka.server.raft;

import lombok.Data;

@Data
public class LogEntry {

    StateMachineCommand command;
    int termNumber;

}
