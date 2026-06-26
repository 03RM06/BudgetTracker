package com.budgettracker.domain;

import java.math.BigDecimal;
import java.time.Instant;

public class Account {

    private long id;
    private long userId;
    private String name;
    private AccountType type;
    private BigDecimal openingBalance;
    private BigDecimal currentBalance;
    private String currency;
    private boolean archived;
    private Instant createdAt;
    private Instant updatedAt;

    public Account() {}

    public Account(long id, long userId, String name, AccountType type,
                   BigDecimal openingBalance, BigDecimal currentBalance,
                   String currency, boolean archived,
                   Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.openingBalance = openingBalance;
        this.currentBalance = currentBalance;
        this.currency = currency;
        this.archived = archived;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public AccountType getType() { return type; }
    public void setType(AccountType type) { this.type = type; }

    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }

    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
