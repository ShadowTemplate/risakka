package risakka.raft.persistence.unit;

import risakka.raft.persistence.Durable;
import risakka.raft.persistence.StorageException;

public class FaultyDiskStorage implements StorageUnit {

    public void store(Durable object) throws StorageException {
        // TODO write on disk, fail randomly
    }
}