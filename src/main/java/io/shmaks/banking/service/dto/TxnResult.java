package io.shmaks.banking.service.dto;

import io.shmaks.banking.model.*;

import java.math.BigDecimal;

public class TxnResult {

    private Long txnId;
    private Long accountId;
    private BigDecimal amount;
    private String currencyCode;
    private TxnStatus status;
    private String createdAt;

    public TxnResult(Txn txn, Account account) {
        this.txnId = txn.getId();
        this.accountId = txn.getAccountId();
        this.amount = txn.getAmount();
        this.currencyCode = account.getCurrencyCode();
        this.status = txn.getStatus();
        this.createdAt = txn.getCreatedAt().toString();
    }

    public Long getTxnId() {
        return txnId;
    }

    public void setTxnId(Long txnId) {
        this.txnId = txnId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public TxnStatus getStatus() {
        return status;
    }

    public void setStatus(TxnStatus status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
