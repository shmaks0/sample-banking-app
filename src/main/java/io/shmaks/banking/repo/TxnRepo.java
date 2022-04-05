package io.shmaks.banking.repo;

import io.shmaks.banking.model.Txn;
import io.shmaks.banking.model.TxnSpendingType;
import reactor.core.publisher.Mono;

public interface TxnRepo {
    Mono<Txn> create(Txn txn);

    Mono<Void> link(Txn txn1, Txn txn2);

    Mono<Txn> findByTxnGroupIdAndAccountIdAndSpendingType(Long txnGroupId, Long accountId, TxnSpendingType spendingType);
}
