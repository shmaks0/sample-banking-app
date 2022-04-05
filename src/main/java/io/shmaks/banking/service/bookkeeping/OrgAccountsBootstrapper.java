package io.shmaks.banking.service.bookkeeping;

import io.shmaks.banking.ext.MockCurrencyService;
import io.shmaks.banking.model.Account;
import io.shmaks.banking.model.AccountType;
import io.shmaks.banking.repo.AccountRepo;
import io.shmaks.banking.service.AccountNumberGenerator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

public class OrgAccountsBootstrapper {
    public static final String ORG_ID = UUID.randomUUID().toString();

    private final MockCurrencyService currencyService;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AccountRepo accountRepo;

    public OrgAccountsBootstrapper(
            MockCurrencyService currencyService,
            AccountNumberGenerator accountNumberGenerator,
            AccountRepo accountRepo) {
        this.currencyService = currencyService;
        this.accountNumberGenerator = accountNumberGenerator;
        this.accountRepo = accountRepo;
    }

    public void bootstrap() {
        currencyService.supportedCurrencies()
                .flatMap(currencies ->
                        Mono.when(currencies.stream().map(currency ->
                            Mono.zip(
                                    accountRepo.create(baseAccount(currency)),
                                    accountRepo.create(feeAccount(currency))
                            )
                        ).collect(Collectors.toList()))
                ).block();
    }

    private Account baseAccount(String currencyCode) {
        var baseAccount = new Account();
        baseAccount.setOwnerId(ORG_ID);
        baseAccount.setBalance(BigDecimal.valueOf(1_000_000));
        baseAccount.setCurrencyCode(currencyCode);
        baseAccount.setAccountNumber(accountNumberGenerator.nextNumber());
        baseAccount.setType(AccountType.BASE);
        baseAccount.setDisplayedName("Base:" + currencyCode);
        baseAccount.setCreatedAt(Instant.now());
        return baseAccount;
    }

    private Account feeAccount(String currencyCode) {
        var feeAccount = new Account();
        feeAccount.setOwnerId(ORG_ID);
        feeAccount.setBalance(BigDecimal.ZERO);
        feeAccount.setCurrencyCode(currencyCode);
        feeAccount.setAccountNumber(accountNumberGenerator.nextNumber());
        feeAccount.setType(AccountType.FEE);
        feeAccount.setDisplayedName("Fee:" + currencyCode);
        feeAccount.setCreatedAt(Instant.now());
        return feeAccount;
    }
}
