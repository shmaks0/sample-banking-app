package io.shmaks.banking.repo;

import io.shmaks.banking.model.Account;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InMemoryAccountRepo implements AccountRepo {

    private final Map<Long, Account> accountsById = new ConcurrentHashMap<>();
    private final NavigableMap<String, Account> accountsByNumber = new ConcurrentSkipListMap<>();

    private final AtomicLong SEQ = new AtomicLong(100500);

    @Override
    public Mono<Account> findById(Long id) {
        return Mono.justOrEmpty(accountsById.get(id));
    }

    @Override
    public Mono<Account> findByAccountNumber(String accountNumber) {
        return Mono.justOrEmpty(accountsByNumber.get(accountNumber));
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
        var accounts = afterAccountNumber == null
                ? accountsByNumber.values() : accountsByNumber.tailMap(afterAccountNumber, false).values();
        return Mono.just(
                filter.apply(accounts.stream().filter(acc -> acc.getDeletedAt() == null))
                        .limit(count)
                        .collect(Collectors.toList())
        );
    }

    @Override
    public Mono<Account> create(Account newAccount) {
        if (accountsByNumber.putIfAbsent(newAccount.getAccountNumber(), newAccount) != null) {
            return Mono.error(new DataInconsistencyException("ACC_NUM_UC"));
        }
        newAccount.setId(SEQ.getAndIncrement());
        newAccount.setCreatedAt(Instant.now());
        accountsById.put(newAccount.getId(), newAccount);
        return Mono.just(newAccount);
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
}
