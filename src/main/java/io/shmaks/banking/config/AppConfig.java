package io.shmaks.banking.config;

import io.shmaks.banking.ext.CurrencyService;
import io.shmaks.banking.ext.MockCurrencyService;
import io.shmaks.banking.repo.AccountRepo;
import io.shmaks.banking.repo.InMemoryAccountRepo;
import io.shmaks.banking.service.AccountNumberGenerator;
import io.shmaks.banking.service.AccountService;
import io.shmaks.banking.service.SimpleAccountNumberGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    private final SampleAppExtProps extProps;

    public AppConfig(SampleAppExtProps extProps) {
        this.extProps = extProps;
    }

    @Bean
    public AccountService accountService(
            CurrencyService currencyService,
            AccountNumberGenerator accountNumberGenerator,
            AccountRepo repo) {
        return new AccountService(repo, currencyService, accountNumberGenerator);
    }

    @Bean
    public CurrencyService currencyService() {
        return new MockCurrencyService(extProps);
    }

    @Bean
    public AccountNumberGenerator accountNumberGenerator() {
        return new SimpleAccountNumberGenerator();
    }

    @Bean
    public AccountRepo accountRepo() {
        return new InMemoryAccountRepo();
    }
}
