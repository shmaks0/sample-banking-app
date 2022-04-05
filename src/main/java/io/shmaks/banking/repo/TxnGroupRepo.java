package io.shmaks.banking.repo;

import io.shmaks.banking.model.TxnGroup;
import reactor.core.publisher.Mono;

import java.util.UUID;


public interface TxnGroupRepo {
    Mono<CreationResult> merge(TxnGroup group);
    Mono<TxnGroup> findByUUID(UUID txnUUID);

    class CreationResult {
        private final boolean createdNew;
        private final TxnGroup txnGroup;

        public CreationResult(boolean createdNew, TxnGroup txnGroup) {
            this.createdNew = createdNew;
            this.txnGroup = txnGroup;
        }

        public boolean isCreatedNew() {
            return createdNew;
        }

        public TxnGroup getTxnGroup() {
            return txnGroup;
        }
    }
}
