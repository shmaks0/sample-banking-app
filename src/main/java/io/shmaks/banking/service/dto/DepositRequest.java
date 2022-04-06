package io.shmaks.banking.service.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

@Validated
public class DepositRequest extends MoneyRequest {

    @NotBlank
    private final String accountNumber;

    @NotBlank
    private final String currencyCode;

    @JsonCreator
    public DepositRequest(
            @JsonProperty("accountNumber") String accountNumber,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currencyCode") String currencyCode,
            @JsonProperty("comment") String comment) {
        super(amount, comment);
        this.accountNumber = accountNumber;
        this.currencyCode = currencyCode;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    @Override
    @JsonIgnore
    public String getPayerAccountNumber() {
        return null;
    }

    @Override
    @JsonIgnore
    public String getReceiverAccountNumber() {
        return accountNumber;
    }

    @Override
    public String toString() {
        return "DepositRequest{" +
                super.toString() + "," +
                "accountNumber='" + accountNumber + '\'' +
                "currencyCode='" + currencyCode + '\'' +
                '}';
    }
}
