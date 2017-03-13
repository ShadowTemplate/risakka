package risakka.server.raft;

import lombok.Data;

import java.io.Serializable;

@Data
class StateMachineCommand implements Serializable {

    private String command; // could be change to something else
}
