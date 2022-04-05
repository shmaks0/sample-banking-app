package io.shmaks.banking.repo;

import io.shmaks.banking.model.TxnGroup;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryTxnGroupRepo implements TxnGroupRepo {

    private final Map<UUID, TxnGroup> txnGroupsByUUID = new ConcurrentHashMap<>();

    private final AtomicLong SEQ = new AtomicLong(424242);

    @Override
    public Mono<CreationResult> merge(TxnGroup group) {
        var existing = txnGroupsByUUID.putIfAbsent(group.getTxnUUID(), group);
        if (existing != null) {
            return Mono.just(new CreationResult(false, existing));
        }
        group.setId(SEQ.getAndIncrement());
        group.setCreatedAt(Instant.now());
        return Mono.just(new CreationResult(true, group));
    }

    @Override
    public Mono<TxnGroup> findByUUID(UUID txnUUID) {
        return Mono.justOrEmpty(txnGroupsByUUID.get(txnUUID));
    }

    public void clear() {
        txnGroupsByUUID.clear();
    }
}
