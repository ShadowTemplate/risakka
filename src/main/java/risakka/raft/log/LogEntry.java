package risakka.raft.log;

import lombok.Getter;
import risakka.raft.persistence.Durable;

import lombok.AllArgsConstructor;

@Getter // Do not add Setter. Class must me immutable
@AllArgsConstructor
public class LogEntry implements Durable {

    private StateMachineCommand command; // r
    private Integer termNumber; // r
    // private Integer positionInLog;

}
