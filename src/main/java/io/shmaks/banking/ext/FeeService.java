package io.shmaks.banking.ext;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface FeeService {

    Mono<BigDecimal> getExchangeFee(CurrencyPair currencyPair, BigDecimal amount);
    Mono<BigDecimal> getInternationalFee(String currency, BigDecimal amount);
}
