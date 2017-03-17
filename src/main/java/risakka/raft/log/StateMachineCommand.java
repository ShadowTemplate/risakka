package risakka.raft.log;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class StateMachineCommand implements Serializable {

    private String command; // could be change to something else
}
