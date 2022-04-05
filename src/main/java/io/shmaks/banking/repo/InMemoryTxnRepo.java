package io.shmaks.banking.repo;

import io.shmaks.banking.model.Txn;
import io.shmaks.banking.model.TxnSpendingType;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryTxnRepo implements TxnRepo {

    private final Map<Long, Txn> txnById = new ConcurrentHashMap<>();

    private final AtomicLong SEQ = new AtomicLong(500100);

    @Override
    public Mono<Txn> create(Txn txn) {
        txn.setId(SEQ.getAndIncrement());
        txn.setCreatedAt(Instant.now());
        txnById.put(txn.getId(), txn);
        return Mono.just(txn);
    }

    @Override
    public Mono<Void> link(Txn txn1, Txn txn2) {
        txnById.compute(txn1.getId(), (id, txn) -> {
            if (txn != null) {
                txn.setLinkingTxnId(txn2.getId());
            }
            return txn;
        });
        txnById.compute(txn2.getId(), (id, txn) -> {
            if (txn != null) {
                txn.setLinkingTxnId(txn1.getId());
            }
            return txn;
        });
        return Mono.empty();
    }

    @Override
    public Mono<Txn> findByTxnGroupIdAndAccountIdAndSpendingType(Long txnGroupId, Long accountId, TxnSpendingType spendingType) {
        return Mono.justOrEmpty(txnById.values().stream()
                .filter(txn ->
                        txn.getTxnGroupId().equals(txnGroupId) && txn.getAccountId().equals(accountId) &&
                                txn.getSpendingType() == spendingType
                )
                .findFirst()
        );
    }
}
