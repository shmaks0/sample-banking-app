package io.shmaks.banking.service.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

@Validated
public class CreateAccountRequest {
    @Min(0)
    private final BigDecimal initialBalance;
    @NotBlank
    private final String currencyCode;
    @Size(max = 256)
    private final String displayedName;

    @JsonCreator
    public CreateAccountRequest(
            @JsonProperty("initialBalance") BigDecimal initialBalance,
            @JsonProperty("currencyCode") String currencyCode,
            @JsonProperty("displayedName") String displayedName) {
        this.initialBalance = initialBalance != null ? initialBalance : BigDecimal.ZERO;
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

    @Override
    public String toString() {
        return "CreateAccountRequest{" +
                "initialBalance=" + initialBalance +
                ", currencyCode='" + currencyCode + '\'' +
                ", displayedName='" + displayedName + '\'' +
                '}';
    }
}
