package io.shmaks.banking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import java.util.Set;

@ConfigurationProperties("sample-banking-app.ext")
@ConstructorBinding
public class SampleAppExtProps {

    public static final SampleAppExtProps DEFAULT = new SampleAppExtProps(
            Set.of(
                    new CurrenciesExchangeRate("USD", "AED", 3.67, 0.27),
                    new CurrenciesExchangeRate("EUR", "AED", 4.03, 0.25),
                    new CurrenciesExchangeRate("EUR", "USD", 1.1, 0.91)
            ),
            Set.of("DB", "JPM")
    );

    private final Set<CurrenciesExchangeRate> currenciesExchangeRates;
    private final Set<String> correspondentOwners;

    public SampleAppExtProps(Set<CurrenciesExchangeRate> currenciesExchangeRates, Set<String> correspondentOwners) {
        this.currenciesExchangeRates = currenciesExchangeRates != null ? currenciesExchangeRates : DEFAULT.currenciesExchangeRates;
        this.correspondentOwners = correspondentOwners != null ? correspondentOwners : DEFAULT.correspondentOwners;
    }

    public Set<CurrenciesExchangeRate> getCurrenciesExchangeRates() {
        return currenciesExchangeRates;
    }

    public Set<String> getCorrespondentOwners() {
        return correspondentOwners;
    }

    @ConstructorBinding
    public static class CurrenciesExchangeRate {
        private final String from;
        private final String to;
        private final double rate;
        private final double reverseRate;

        public CurrenciesExchangeRate(String from, String to, double rate, double reverseRate) {
            this.from = from;
            this.to = to;
            this.rate = rate;
            this.reverseRate = reverseRate;
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }

        public double getRate() {
            return rate;
        }

        public double getReverseRate() {
            return reverseRate;
        }
    }
}
