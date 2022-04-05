package io.shmaks.banking.service.dto;

import java.math.BigDecimal;

public class BalanceResponse {

    private final BigDecimal amount;

    public BalanceResponse(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
