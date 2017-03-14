package risakka.server.rpc;


import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class RequestVoteRequest implements Serializable {

    private Integer term;
    // candidateId can be retrieved via Akka's getSender() method
    private Integer lastLogIndex;
    private Integer lastLogTerm;

}
