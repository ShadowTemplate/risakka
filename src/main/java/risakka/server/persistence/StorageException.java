package risakka.server.persistence;

public class StorageException extends RuntimeException {

    public StorageException() {
        super();
    }

    public StorageException(String message) {
        super(message);
    }
}
