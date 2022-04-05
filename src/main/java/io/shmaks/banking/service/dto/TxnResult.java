package io.shmaks.banking.service.dto;

import io.shmaks.banking.model.*;

import java.math.BigDecimal;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TxnResult txnResult = (TxnResult) o;
        return Objects.equals(txnId, txnResult.txnId) && Objects.equals(accountId, txnResult.accountId) && Objects.equals(amount, txnResult.amount) && Objects.equals(currencyCode, txnResult.currencyCode) && status == txnResult.status && Objects.equals(createdAt, txnResult.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(txnId, accountId, amount, currencyCode, status, createdAt);
    }
}
