package io.shmaks.banking.ext;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MockFeeService implements FeeService {

    @Override
    public Mono<BigDecimal> getExchangeFee(CurrencyPair currencyPair, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.TEN) < 0) {
            return Mono.just(BigDecimal.ZERO);
        } else if (amount.compareTo(BigDecimal.valueOf(100)) < 0) {
            return Mono.just(BigDecimal.ONE);
        } else {
            return Mono.just(amount.divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP));
        }
    }

    @Override
    public Mono<BigDecimal> getInternationalFee(String currency, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.TEN) < 0) {
            return Mono.just(amount.divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP));
        } else if (amount.compareTo(BigDecimal.valueOf(10)) < 0) {
            return Mono.just(BigDecimal.ONE);
        } else {
            return Mono.just(amount.divide(BigDecimal.valueOf(50), RoundingMode.HALF_UP));
        }
    }
}
