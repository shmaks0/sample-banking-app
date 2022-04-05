package io.shmaks.banking.service;

public class RetryLaterException extends RuntimeException {
    public RetryLaterException() {
        super("Retry operation later");
    }
}
