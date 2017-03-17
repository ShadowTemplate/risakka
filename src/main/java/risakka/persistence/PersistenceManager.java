package risakka.persistence;

import risakka.persistence.unit.DiskStorage;
import risakka.persistence.unit.StorageUnit;

public enum PersistenceManager {
    instance;

    private StorageUnit storageUnit;

    PersistenceManager() {
        if (storageUnit == null) {
            storageUnit = new DiskStorage(); // TODO should be defined by a property
        }
    }

    public void persist(Durable object) throws StorageException {
        storageUnit.store(object);
    }
}
