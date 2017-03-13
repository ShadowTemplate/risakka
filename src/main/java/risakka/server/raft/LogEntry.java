package risakka.server.raft;

import lombok.Data;
import risakka.server.persistence.Durable;

import java.io.Serializable;

@Data
public class LogEntry implements Durable, Serializable{

    private StateMachineCommand command; // r
    private Integer termNumber; // r
    private Integer positionInLog;

}
