package risakka.raft.log;

import lombok.Getter;

import lombok.AllArgsConstructor;

import java.io.Serializable;

@Getter // Do not add Setter. Class must me immutable
@AllArgsConstructor
public class LogEntry implements Serializable {

    private StateMachineCommand command; // r
    private Integer termNumber; // r
    // private Integer positionInLog;

}
