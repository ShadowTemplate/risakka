package risakka.raft.persistence.unit;

import risakka.raft.persistence.Durable;
import risakka.raft.persistence.StorageException;

public interface StorageUnit {

    void store(Durable object) throws StorageException;
}
