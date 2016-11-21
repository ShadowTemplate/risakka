package risakka.server.persistence;

interface StorageUnit {

    void store(Durable object) throws Exception;
}
