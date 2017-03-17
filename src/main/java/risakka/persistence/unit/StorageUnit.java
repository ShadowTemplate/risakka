package risakka.persistence.unit;

import risakka.persistence.Durable;
import risakka.persistence.StorageException;

public interface StorageUnit {

    void store(Durable object) throws StorageException;
}
