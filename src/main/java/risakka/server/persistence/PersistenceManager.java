package risakka.server.persistence;

public enum PersistenceManager {
    instance;

    private StorageUnit storageUnit;

    PersistenceManager() {
        if (storageUnit == null) {
            storageUnit = new DiskStorage(); // TODO should be defined by a property
        }
    }

    public void persist(Durable object) throws Exception {
        storageUnit.store(object);
    }
}
