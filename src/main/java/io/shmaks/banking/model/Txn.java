package io.shmaks.banking.model;

import java.math.BigDecimal;
import java.time.Instant;

public class Txn {

    private Long id;
    private Long accountId;
    private Long txnGroupId;
    private BigDecimal amount;
    private TxnStatus status;
    private Long linkingTxnId;
    private TxnSpendingType spendingType;
    private String details;
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getTxnGroupId() {
        return txnGroupId;
    }

    public void setTxnGroupId(Long txnGroupId) {
        this.txnGroupId = txnGroupId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public TxnStatus getStatus() {
        return status;
    }

    public void setStatus(TxnStatus status) {
        this.status = status;
    }

    public Long getLinkingTxnId() {
        return linkingTxnId;
    }

    public void setLinkingTxnId(Long linkingTxnId) {
        this.linkingTxnId = linkingTxnId;
    }

    public TxnSpendingType getSpendingType() {
        return spendingType;
    }

    public void setSpendingType(TxnSpendingType spendingType) {
        this.spendingType = spendingType;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
