package io.shmaks.banking.repo;

import io.shmaks.banking.model.Account;
import reactor.core.publisher.Mono;

import java.util.List;

public interface AccountRepo {
    Mono<Account> findById(Long id);
    Mono<Account> findByAccountNumber(String accountNumber);

    Mono<List<Account>> findAllByOwnerIdOrderByAccountNumberAsc(String ownerId, int count, String afterAccountNumber);
    Mono<List<Account>> findAllOrderByAccountNumberAsc(int count, String afterAccountNumber);

    Mono<Account> create(Account newAccount);
    Mono<Boolean> deleteByIdAndOwnerId(Long id, String ownerId);

}
