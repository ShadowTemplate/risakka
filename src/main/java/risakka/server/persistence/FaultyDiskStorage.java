package risakka.server.persistence;

class FaultyDiskStorage implements StorageUnit {

    public void store(Durable object) throws Exception {
        // write on disk, fail randomly
    }
}
