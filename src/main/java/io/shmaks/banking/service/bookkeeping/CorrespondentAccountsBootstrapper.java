package io.shmaks.banking.service.bookkeeping;

import io.shmaks.banking.config.SampleAppExtProps;
import io.shmaks.banking.ext.MockCurrencyService;
import io.shmaks.banking.model.Account;
import io.shmaks.banking.model.AccountType;
import io.shmaks.banking.repo.AccountRepo;
import io.shmaks.banking.service.AccountNumberGenerator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Collectors;

public class CorrespondentAccountsBootstrapper {

    private final MockCurrencyService currencyService;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AccountRepo accountRepo;
    private final SampleAppExtProps extProps;

    public CorrespondentAccountsBootstrapper(
            MockCurrencyService currencyService,
            AccountNumberGenerator accountNumberGenerator,
            AccountRepo accountRepo,
            SampleAppExtProps extProps) {
        this.currencyService = currencyService;
        this.accountNumberGenerator = accountNumberGenerator;
        this.accountRepo = accountRepo;
        this.extProps = extProps;
    }

    public void bootstrap() {
        currencyService.supportedCurrencies()
                .flatMap(currencies ->
                        Mono.when(currencies.stream().flatMap(currency ->
                                extProps.getCorrespondentOwners().stream().map(correspondent ->
                                        accountRepo.create(correspondentAccount(currency, correspondent))
                                )
                        ).collect(Collectors.toList()))
                ).block();
    }

    private Account correspondentAccount(String currencyCode, String correspondentOwnerId) {
        var feeAccount = new Account();
        feeAccount.setOwnerId(correspondentOwnerId);
        feeAccount.setBalance(BigDecimal.ZERO);
        feeAccount.setCurrencyCode(currencyCode);
        feeAccount.setAccountNumber(accountNumberGenerator.nextNumber());
        feeAccount.setType(AccountType.CORRESPONDENT);
        feeAccount.setDisplayedName("Correspondent for " + correspondentOwnerId + ", currency:" + currencyCode);
        feeAccount.setCreatedAt(Instant.now());
        return feeAccount;
    }
}
