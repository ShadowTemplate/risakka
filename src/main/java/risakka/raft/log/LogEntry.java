package risakka.raft.log;

import lombok.Getter;

import lombok.AllArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

@Getter // Do not add Setter. Class must me immutable
@AllArgsConstructor
@ToString
public class LogEntry implements Serializable {

    private Integer termNumber; // r
    private StateMachineCommand command; // r
    // private Integer positionInLog;

}
