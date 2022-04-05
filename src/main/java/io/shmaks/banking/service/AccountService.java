package io.shmaks.banking.service;

import io.shmaks.banking.ext.CurrencyService;
import io.shmaks.banking.model.Account;
import io.shmaks.banking.model.AccountType;
import io.shmaks.banking.repo.AccountRepo;
import io.shmaks.banking.service.dto.AccountResponse;
import io.shmaks.banking.service.dto.CreateAccountRequest;
import io.shmaks.banking.service.dto.Pagination;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

public class AccountService {

    private final AccountRepo repo;
    private final CurrencyService currencyService;
    private final AccountNumberGenerator numberGenerator;

    public AccountService(AccountRepo repo, CurrencyService currencyService, AccountNumberGenerator numberGenerator) {
        this.repo = repo;
        this.currencyService = currencyService;
        this.numberGenerator = numberGenerator;
    }

    @Transactional(readOnly = true)
    public Mono<Account> findById(Long id) {
        return repo.findById(id);
    }

    @Transactional(readOnly = true)
    public Mono<List<AccountResponse>> findUserAccounts(String ownerId, Pagination<String> pagination) {
        return repo.findAllUserAccountsByOwnerIdOrderByAccountNumberAsc(ownerId, pagination.getCount(), pagination.getAfter())
                .map(list -> list.stream().map(AccountResponse::new).collect(Collectors.toList()));
    }

    @Transactional(readOnly = true)
    public Mono<List<AccountResponse>> findAllUserAccounts(Pagination<String> pagination) {
        return repo.findAllUserAccountsOrderByAccountNumberAsc(pagination.getCount(), pagination.getAfter())
                .map(list -> list.stream().map(AccountResponse::new).collect(Collectors.toList()));
    }

    @Transactional
    public Mono<Account> create(String ownerId, CreateAccountRequest request) {
        return currencyService.supports(request.getCurrencyCode())
                .flatMap(supported -> {
                    if (supported) {
                        var newAccount = new Account();
                        newAccount.setOwnerId(ownerId);
                        newAccount.setBalance(request.getInitialBalance());
                        newAccount.setCurrencyCode(request.getCurrencyCode());
                        newAccount.setDisplayedName(request.getDisplayedName());
                        newAccount.setAccountNumber(numberGenerator.nextNumber());
                        newAccount.setType(AccountType.USER);
                        return repo.create(newAccount);
                    } else {
                        return Mono.error(new BusinessLogicError("currency is not supported"));
                    }
                });
    }

    @Transactional
    public Mono<Boolean> deleteById(Long id, String ownerId) {
        return repo.deleteByIdAndOwnerId(id, ownerId);
    }

}
