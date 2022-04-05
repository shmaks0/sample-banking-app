package io.shmaks.banking.ext;

import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public interface CurrencyService {

    Mono<Boolean> supports(String... currencyCodes);
    Mono<Map<CurrencyPair, Double>> getRates(Collection<CurrencyPair> pairs);

}
