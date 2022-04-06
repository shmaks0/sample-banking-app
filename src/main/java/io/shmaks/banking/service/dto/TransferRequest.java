package io.shmaks.banking.service.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.Objects;

@Validated
public class TransferRequest extends MoneyRequest {

    @NotBlank
    private final String payerAccountNumber;

    @NotBlank
    private final String receiverAccountNumber;

    @JsonCreator
    public TransferRequest(
            @JsonProperty("payerAccountNumber") String payerAccountNumber,
            @JsonProperty("receiverAccountNumber") String receiverAccountNumber,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("comment") String comment) {
        super(amount, comment);
        this.payerAccountNumber = payerAccountNumber;
        this.receiverAccountNumber = receiverAccountNumber;
    }

    @Override
    @JsonProperty("payerAccountNumber")
    public String getPayerAccountNumber() {
        return payerAccountNumber;
    }

    @Override
    @JsonProperty("receiverAccountNumber")
    public String getReceiverAccountNumber() {
        return receiverAccountNumber;
    }

    @JsonIgnore
    @AssertTrue(message = "payerAccountNumber & receiverAccountNumber should be different")
    public boolean isAccountsDifferent() {
        return payerAccountNumber != null && receiverAccountNumber != null && !Objects.equals(payerAccountNumber, receiverAccountNumber);
    }

    @Override
    public String toString() {
        return "TransferRequest{" +
                super.toString() + "," +
                "payerAccountNumber='" + payerAccountNumber + '\'' +
                "receiverAccountNumber='" + receiverAccountNumber + '\'' +
                '}';
    }
}
