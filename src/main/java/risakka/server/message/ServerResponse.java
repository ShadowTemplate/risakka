package risakka.server.message;

import akka.actor.ActorRef;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ServerResponse {

    private Integer requestId;
    private ActorRef leader;

}
