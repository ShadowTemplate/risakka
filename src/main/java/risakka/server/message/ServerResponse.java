package risakka.server.message;

import akka.actor.ActorRef;
import lombok.AllArgsConstructor;
import lombok.Data;
import risakka.server.raft.Status;

@Data
@AllArgsConstructor
public class ServerResponse {

    private Status status;
    private Integer requestId;
    private ActorRef leader;

}
