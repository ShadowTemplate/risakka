package risakka.raft.log;

import lombok.Getter;

import lombok.AllArgsConstructor;
import lombok.ToString;
import risakka.util.ImmutableCopy;

import java.io.Serializable;

@Getter // Do not add Setter. Class must me immutable
@AllArgsConstructor
@ToString
public class LogEntry implements Serializable, ImmutableCopy<LogEntry> {

    private Integer termNumber; // r
    private StateMachineCommand command; // r
    // private Integer positionInLog;

    @Override
    public LogEntry immutableCopy() {
        return new LogEntry(termNumber, new StateMachineCommand(command));
    }

}
