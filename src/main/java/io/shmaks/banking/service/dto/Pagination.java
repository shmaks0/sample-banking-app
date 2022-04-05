package io.shmaks.banking.service.dto;

public class Pagination<T> {
    private final int count;
    private final T after;

    public Pagination(int count, T after) {
        this.count = count;
        this.after = after;
    }

    public int getCount() {
        return count;
    }

    public T getAfter() {
        return after;
    }
}
