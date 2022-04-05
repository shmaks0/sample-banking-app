package io.shmaks.banking.service.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

@Validated
public class WithdrawalRequest extends MoneyRequest {

    @NotBlank
    private final String accountNumber;

    @JsonCreator
    public WithdrawalRequest(
            @JsonProperty("accountNumber") String accountNumber,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currencyCode") String currencyCode,
            @JsonProperty("comment") String comment) {
        super(amount, currencyCode, comment);
        this.accountNumber = accountNumber;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    @Override
    @JsonIgnore
    public String getPayerAccountNumber() {
        return accountNumber;
    }

    @Override
    @JsonIgnore
    public String getReceiverAccountNumber() {
        return null;
    }

    @Override
    public String toString() {
        return "WithdrawalRequest{" +
                super.toString() + "," +
                "accountNumber='" + accountNumber + '\'' +
                '}';
    }
}
