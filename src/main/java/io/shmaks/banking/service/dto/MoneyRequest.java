package io.shmaks.banking.service.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

abstract public class MoneyRequest {
    @Positive
    private final BigDecimal amount;
    @NotBlank
    private final String currencyCode;
    @Size(max = 256)
    private final String comment;

    @JsonCreator
    public MoneyRequest(
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currencyCode") String currencyCode,
            @JsonProperty("comment") String comment) {
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.comment = comment;
    }

    @JsonIgnore
    public abstract String getPayerAccountNumber();
    @JsonIgnore
    public abstract String getReceiverAccountNumber();

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getComment() {
        return comment;
    }

    @Override
    public String toString() {
        return "DepositRequest{" +
                ", amount=" + amount +
                ", currencyCode='" + currencyCode + '\'' +
                ", comment='" + comment + '\'' +
                '}';
    }
}
