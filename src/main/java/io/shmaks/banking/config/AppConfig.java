package io.shmaks.banking.config;

import io.shmaks.banking.ext.CurrencyService;
import io.shmaks.banking.ext.FeeService;
import io.shmaks.banking.ext.MockCurrencyService;
import io.shmaks.banking.ext.MockFeeService;
import io.shmaks.banking.repo.*;
import io.shmaks.banking.service.AccountNumberGenerator;
import io.shmaks.banking.service.AccountService;
import io.shmaks.banking.service.SimpleAccountNumberGenerator;
import io.shmaks.banking.service.TransferService;
import io.shmaks.banking.service.bookkeeping.CorrespondentAccountsBootstrapper;
import io.shmaks.banking.service.bookkeeping.OrgAccountsBootstrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    private final SampleAppExtProps extProps;

    public AppConfig(SampleAppExtProps extProps) {
        this.extProps = extProps;
    }

    @Bean
    public TransferService transferService(
            CurrencyService currencyService,
            FeeService feeService,
            TxnGroupRepo txnGroupRepo,
            TxnRepo txnRepo,
            AccountRepo accountRepo) {
        return new TransferService(txnGroupRepo, txnRepo, accountRepo, currencyService, feeService);
    }

    @Bean
    public AccountService accountService(
            CurrencyService currencyService,
            AccountNumberGenerator accountNumberGenerator,
            AccountRepo repo) {
        return new AccountService(repo, currencyService, accountNumberGenerator);
    }

    @Bean
    public MockCurrencyService currencyService() {
        return new MockCurrencyService(extProps);
    }

    @Bean
    public FeeService feeService() {
        return new MockFeeService();
    }

    @Bean
    public AccountNumberGenerator accountNumberGenerator() {
        return new SimpleAccountNumberGenerator();
    }

    @Bean
    public AccountRepo accountRepo() {
        return new InMemoryAccountRepo();
    }

    @Bean
    public TxnGroupRepo txnGroupRepo() {
        return new InMemoryTxnGroupRepo();
    }

    @Bean
    public TxnRepo txnRepo() {
        return new InMemoryTxnRepo();
    }

    //----Helpers----

    @Bean(initMethod = "bootstrap")
    public OrgAccountsBootstrapper orgAccountsBootstrapper(
            MockCurrencyService currencyService,
            AccountNumberGenerator accountNumberGenerator,
            AccountRepo repo) {
        return new OrgAccountsBootstrapper(currencyService, accountNumberGenerator, repo);
    }

    @Bean(initMethod = "bootstrap")
    public CorrespondentAccountsBootstrapper correspondentAccountsBootstrapper(
            MockCurrencyService currencyService,
            AccountNumberGenerator accountNumberGenerator,
            AccountRepo repo) {
        return new CorrespondentAccountsBootstrapper(currencyService, accountNumberGenerator, repo, extProps);
    }
}
