package risakka.server.rpc;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class AppendEntriesResponse implements Serializable {

    private Integer term;
    private Boolean success;
}
