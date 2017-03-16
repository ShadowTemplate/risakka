package risakka.server.persistence;

class FaultyDiskStorage implements StorageUnit {

    public void store(Durable object) throws StorageException {
        // TODO write on disk, fail randomly
    }
}
