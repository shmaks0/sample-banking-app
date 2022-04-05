package io.shmaks.banking.service;

import java.util.concurrent.atomic.AtomicLong;

public class SimpleAccountNumberGenerator implements AccountNumberGenerator {
    private final AtomicLong nextNumber = new AtomicLong(1L << 60);

    @Override
    public String nextNumber() {
        return String.valueOf(nextNumber.getAndAdd(13));
    }
}
