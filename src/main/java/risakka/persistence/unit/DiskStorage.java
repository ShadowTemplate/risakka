package risakka.persistence.unit;

import risakka.persistence.Durable;
import risakka.persistence.StorageException;

public class DiskStorage implements StorageUnit {

    public void store(Durable object) throws StorageException {
        // TODO write on disk
    }
}
