package com.budgettracker.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class Transaction {

    private long id;
    private long userId;
    private long accountId;
    private Long categoryId;
    private TxDirection direction;
    private TxStatus status = TxStatus.POSTED;
    private BigDecimal amount;
    private String currency;
    private LocalDate occurredOn;
    private Instant occurredAt;
    private IncomeNature incomeNature;
    private String description;
    private Long recurringId;
    private Long transferAccountId;
    private BigDecimal exchangeRate;
    private Instant createdAt;
    private Instant updatedAt;

    public Transaction() {}

    public Transaction(long id, long userId, long accountId, Long categoryId,
                       TxDirection direction, BigDecimal amount, String currency,
                       LocalDate occurredOn, Instant occurredAt, IncomeNature incomeNature,
                       String description, Long recurringId, Long transferAccountId,
                       BigDecimal exchangeRate, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
        this.occurredOn = occurredOn;
        this.occurredAt = occurredAt;
        this.incomeNature = incomeNature;
        this.description = description;
        this.recurringId = recurringId;
        this.transferAccountId = transferAccountId;
        this.exchangeRate = exchangeRate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public long getAccountId() { return accountId; }
    public void setAccountId(long accountId) { this.accountId = accountId; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public TxDirection getDirection() { return direction; }
    public void setDirection(TxDirection direction) { this.direction = direction; }

    public TxStatus getStatus() { return status; }
    public void setStatus(TxStatus status) { this.status = status; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDate getOccurredOn() { return occurredOn; }
    public void setOccurredOn(LocalDate occurredOn) { this.occurredOn = occurredOn; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public IncomeNature getIncomeNature() { return incomeNature; }
    public void setIncomeNature(IncomeNature incomeNature) { this.incomeNature = incomeNature; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getRecurringId() { return recurringId; }
    public void setRecurringId(Long recurringId) { this.recurringId = recurringId; }

    public Long getTransferAccountId() { return transferAccountId; }
    public void setTransferAccountId(Long transferAccountId) { this.transferAccountId = transferAccountId; }

    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
