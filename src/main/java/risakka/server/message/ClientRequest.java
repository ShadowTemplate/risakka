package risakka.server.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import risakka.server.raft.StateMachineCommand;

@Data
@AllArgsConstructor
public class ClientRequest {

    private Integer requestId;
    private StateMachineCommand command;
}
