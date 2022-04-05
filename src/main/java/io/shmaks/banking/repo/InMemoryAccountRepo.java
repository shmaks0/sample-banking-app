package io.shmaks.banking.repo;

import io.shmaks.banking.model.Account;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InMemoryAccountRepo implements AccountRepo {

    private static class AccountWithLock {
        private final Account account;
        private final ReentrantLock lock;

        public AccountWithLock(Account account, ReentrantLock lock) {
            this.account = account;
            this.lock = lock;
        }
    }

    private final Map<Long, Account> accountsById = new ConcurrentHashMap<>();
    private final NavigableMap<String, AccountWithLock> accountsByNumber = new ConcurrentSkipListMap<>();

    private final AtomicLong SEQ = new AtomicLong(100500);

    @Override
    public Mono<Account> findById(Long id) {
        return Mono.justOrEmpty(accountsById.get(id));
    }

    @Override
    public Mono<Account> findByAccountNumber(String accountNumber) {
        return Mono.justOrEmpty(accountsByNumber.get(accountNumber)).map(it -> it.account);
    }

    @Override
    public Mono<List<Account>> findAllByOwnerIdOrderByAccountNumberAsc(String ownerId, int count, String afterAccountNumber) {
        return listAccounts(count, afterAccountNumber, stream -> stream.filter(acc -> acc.getOwnerId().equals(ownerId)));
    }

    @Override
    public Mono<List<Account>> findAllOrderByAccountNumberAsc(int count, String afterAccountNumber) {
        return listAccounts(count, afterAccountNumber, UnaryOperator.identity());
    }

    private Mono<List<Account>> listAccounts(int count, String afterAccountNumber, UnaryOperator<Stream<Account>> filter) {
        var accounts = (afterAccountNumber == null || !accountsByNumber.containsKey(afterAccountNumber))
                ? accountsByNumber.values() : accountsByNumber.tailMap(afterAccountNumber, false).values();
        return Mono.just(
                filter.apply(accounts.stream().map(it -> it.account).filter(acc -> acc.getDeletedAt() == null))
                        .limit(count)
                        .collect(Collectors.toList())
        );
    }

    @Override
    public Mono<Account> create(Account newAccount) {
        if (accountsByNumber.putIfAbsent(newAccount.getAccountNumber(), new AccountWithLock(newAccount, new ReentrantLock())) != null) {
            return Mono.error(new DataInconsistencyException("ACC_NUM_UC"));
        }
        newAccount.setId(SEQ.getAndIncrement());
        newAccount.setCreatedAt(Instant.now());
        accountsById.put(newAccount.getId(), newAccount);
        return Mono.just(newAccount);
    }

    @Override
    public Mono<Account> updateBalance(Long accountId, Long txnId, BigDecimal delta) {
        return Mono.justOrEmpty(accountsById.compute(accountId, (id, acc) -> {
                    if (acc != null) {
                        acc.setBalance(acc.getBalance().add(delta));
                        acc.setLastTxnId(txnId);
                    }
                    return acc;
                })
        );
    }

    @Override
    public Mono<Boolean> deleteByIdAndOwnerId(Long id, String ownerId) {
        var account = accountsById.get(id);
        if (account == null || !account.getOwnerId().equals(ownerId)) {
            return Mono.just(false);
        }
        var willBeDeleted = account.getDeletedAt() == null;
        if (willBeDeleted) {
            account.setDeletedAt(Instant.now());
        }
        return Mono.just(willBeDeleted);
    }

    @Override
    public Mono<LockHandle> selectForUpdate(Collection<String> accountNumbers) {
        var numbers = new ArrayList<>(accountNumbers);
        Collections.sort(numbers);
        final var locks = new ArrayList<ReentrantLock>(accountNumbers.size());
        Runnable unlockAction = () -> {
            for (int i = locks.size() - 1; i >= 0; i--) {
                locks.get(i).unlock();
            }
        };
        var accounts = new HashMap<String, Account>(accountNumbers.size());
        var deadline = System.currentTimeMillis() + 2000;
        try {
            for (var accNumber : numbers) {
                var accountWithLock = accountsByNumber.get(accNumber);
                //noinspection BlockingMethodInNonBlockingContext
                if (accountWithLock != null && accountWithLock.lock.tryLock(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS)) {
                    locks.add(accountWithLock.lock);
                    accounts.put(accNumber, accountWithLock.account);
                }
            }
        } catch (InterruptedException e) {
            unlockAction.run();
            Thread.currentThread().interrupt();
        }

        if (locks.size() != accountNumbers.size()) {
            unlockAction.run();
            accounts.clear();
            return Mono.empty();
        }

        return Mono.just(new LockHandle(accounts, unlockAction));
    }

    public void clear() {
        accountsById.clear();
        accountsByNumber.clear();
    }

    public Collection<Account> getAccounts() {
        return accountsByNumber.values().stream().map(it -> it.account).collect(Collectors.toList());
    }
}
