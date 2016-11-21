package risakka.server.raft;

import lombok.Data;
import risakka.server.persistence.Durable;

@Data
public class LogEntry implements Durable {

    StateMachineCommand command;
    int termNumber;

}
