package io.shmaks.banking.service.dto;

import io.shmaks.banking.model.Account;

import java.math.BigDecimal;
import java.time.Instant;

public class AccountResponse {
    private Long id;
    private String accountNumber;
    private BigDecimal balance;
    private String currencyCode;
    private String displayedName;
    private Long lastTxnId;
    private String createdAt;

    public AccountResponse(Account account) {
        this.id = account.getId();
        this.accountNumber = account.getAccountNumber();
        this.balance = account.getBalance();
        this.currencyCode = account.getCurrencyCode();
        this.displayedName = account.getDisplayedName();
        this.lastTxnId = account.getLastTxnId();
        this.createdAt = account.getCreatedAt().toString();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getDisplayedName() {
        return displayedName;
    }

    public void setDisplayedName(String displayedName) {
        this.displayedName = displayedName;
    }

    public Long getLastTxnId() {
        return lastTxnId;
    }

    public void setLastTxnId(Long lastTxnId) {
        this.lastTxnId = lastTxnId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
