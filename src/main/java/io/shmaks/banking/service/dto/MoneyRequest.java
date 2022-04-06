package io.shmaks.banking.service.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

abstract public class MoneyRequest {
    @Positive
    @NotNull
    private final BigDecimal amount;
    @Size(max = 256)
    private final String comment;

    @JsonCreator
    public MoneyRequest(
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("comment") String comment) {
        this.amount = amount;
        this.comment = comment;
    }

    public abstract String getPayerAccountNumber();
    public abstract String getReceiverAccountNumber();

    public BigDecimal getAmount() {
        return amount;
    }

    public String getComment() {
        return comment;
    }

    @Override
    public String toString() {
        return "MoneyRequest{" +
                "amount=" + amount +
                ", comment='" + comment + '\'' +
                '}';
    }
}
