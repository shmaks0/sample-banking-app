package io.shmaks.banking.service;

public class BusinessLogicError extends RuntimeException {
    public BusinessLogicError(String message) {
        super("emulating business logic: " + message);
    }
}
