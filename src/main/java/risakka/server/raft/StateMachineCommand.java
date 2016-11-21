package risakka.server.raft;

import lombok.Data;

@Data
class StateMachineCommand {

    private String command; // could be change to something else
}
