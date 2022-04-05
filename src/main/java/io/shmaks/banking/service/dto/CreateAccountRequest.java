package io.shmaks.banking.service.dto;

import java.math.BigDecimal;

public class CreateAccountRequest {
    private final BigDecimal initialBalance;
    private final String currencyCode;
    private final String displayedName;

    public CreateAccountRequest(BigDecimal initialBalance, String currencyCode, String displayedName) {
        this.initialBalance = initialBalance;
        this.currencyCode = currencyCode;
        this.displayedName = displayedName;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getDisplayedName() {
        return displayedName;
    }
}
