package risakka.raft.log;

import akka.actor.ActorRef;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class StateMachineCommand implements Serializable {
    
    public StateMachineCommand(String command, Integer clientId, Integer seqNumber) {
        this.command = command;
        this.clientId = clientId;
        this.seqNumber = seqNumber;
    }

    private String command; // could be change to something else
    private Integer clientId; //client that issued the request
    private Integer seqNumber; //null if register request
    private ActorRef clientAddress; //address of actor that issued the request
}
