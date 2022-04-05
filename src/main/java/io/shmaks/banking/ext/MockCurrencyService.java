package io.shmaks.banking.ext;

import io.shmaks.banking.config.SampleAppExtProps;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MockCurrencyService implements CurrencyService {

    private final Map<String, Map<String, Double>> rates;

    public MockCurrencyService(SampleAppExtProps props) {
        var rates = new HashMap<String, Map<String, Double>>();
        props.getCurrenciesExchangeRates().forEach(rate -> {
            rates.putIfAbsent(rate.getFrom(), new HashMap<>());
            rates.get(rate.getFrom()).put(rate.getTo(), rate.getRate());
            rates.putIfAbsent(rate.getTo(), new HashMap<>());
            rates.get(rate.getTo()).put(rate.getFrom(), rate.getReverseRate());
        });
        this.rates = Map.copyOf(rates);
    }

    @Override
    public Mono<Boolean> supports(String... currencyCodes) {
        return Mono.just(rates.keySet().containsAll(List.of(currencyCodes)));
    }

    @Override
    public Mono<Map<CurrencyPair, Double>> getRates(Collection<CurrencyPair> pairs) {
        var result = pairs.stream()
                .map(pair -> new Pair<>(pair, rates.getOrDefault(pair.getFrom(), Map.of()).get(pair.getTo())))
                .filter(it -> it.getSecond() != null)
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));

        return Mono.just(result);
    }

    private static class Pair<T1, T2> {
        private final T1 first;
        private final T2 second;

        public Pair(T1 first, T2 second) {
            this.first = first;
            this.second = second;
        }

        public T1 getFirst() {
            return first;
        }

        public T2 getSecond() {
            return second;
        }
    }
}
