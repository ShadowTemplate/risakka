package risakka.raft.log;

import akka.actor.ActorRef;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
@AllArgsConstructor
@ToString
public class StateMachineCommand implements Serializable {
    
    private String command; // could be change to something else
    private Integer clientId; //client that issued the request
    private Integer seqNumber; //null if register request
    private ActorRef clientAddress; //address of actor that issued the request

    StateMachineCommand(StateMachineCommand stateMachineCommand) {
        this.command = stateMachineCommand.command;
        this.clientId = stateMachineCommand.clientId;
        this.seqNumber = stateMachineCommand.seqNumber;
        this.clientAddress = stateMachineCommand.clientAddress;
    }

    public StateMachineCommand(String command, Integer clientId, Integer seqNumber) {
        this.command = command;
        this.clientId = clientId;
        this.seqNumber = seqNumber;
    }

}
