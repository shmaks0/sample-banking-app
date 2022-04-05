package io.shmaks.banking.repo;

import io.shmaks.banking.model.Account;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public interface AccountRepo {
    Mono<Account> findById(Long id);
    Mono<Account> findByAccountNumber(String accountNumber);

    Mono<List<Account>> findAllByOwnerIdOrderByAccountNumberAsc(String ownerId, int count, String afterAccountNumber);
    Mono<List<Account>> findAllOrderByAccountNumberAsc(int count, String afterAccountNumber);

    Mono<Account> create(Account newAccount);
    Mono<Account> updateBalance(Long accountId, Long txnId, BigDecimal delta);
    Mono<Boolean> deleteByIdAndOwnerId(Long id, String ownerId);

    Mono<LockHandle> selectForUpdate(Collection<String> accountNumbers);

    class LockHandle implements AutoCloseable { // it's not portable to relational case
        final Map<String, Account> accounts;
        final Runnable unlockAction;

        LockHandle(Map<String, Account> accounts, Runnable unlockAction) {
            this.unlockAction = unlockAction;
            this.accounts = accounts;
        }

        @Override
        public void close() {
            unlockAction.run();
        }

        public Map<String, Account> getAccounts() {
            return accounts;
        }
    }
}
