package io.shmaks.banking.repo;

public class DataInconsistencyException extends RuntimeException {
    public DataInconsistencyException(String message) {
        super("emulating constraint violation: " + message);
    }
}
