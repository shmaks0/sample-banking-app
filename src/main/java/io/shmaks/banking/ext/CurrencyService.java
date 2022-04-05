package io.shmaks.banking.ext;

import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public interface CurrencyService {

    class CurrencyPair {
        private final String from;
        private final String to;

        public CurrencyPair(String from, String to) {
            this.from = from;
            this.to = to;
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CurrencyPair that = (CurrencyPair) o;
            return Objects.equals(from, that.from) && Objects.equals(to, that.to);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to);
        }
    }

    Mono<Boolean> supports(String... currencyCodes);

    Mono<Map<CurrencyPair, Double>> getRates(Collection<CurrencyPair> pairs);

}
