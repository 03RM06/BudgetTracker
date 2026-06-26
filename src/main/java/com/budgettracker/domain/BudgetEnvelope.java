package com.budgettracker.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class BudgetEnvelope {

    private long id;
    private long userId;
    private long categoryId;
    private String name;
    private PeriodType periodType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal limitAmount;
    private boolean rollover;
    private BigDecimal alertThresholdPct;
    private String zoneId;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public BudgetEnvelope() {}

    public BudgetEnvelope(long id, long userId, long categoryId, String name,
                          PeriodType periodType, LocalDate periodStart, LocalDate periodEnd,
                          BigDecimal limitAmount, boolean rollover, BigDecimal alertThresholdPct,
                          String zoneId, boolean active,
                          Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.categoryId = categoryId;
        this.name = name;
        this.periodType = periodType;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.limitAmount = limitAmount;
        this.rollover = rollover;
        this.alertThresholdPct = alertThresholdPct;
        this.zoneId = zoneId;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public long getCategoryId() { return categoryId; }
    public void setCategoryId(long categoryId) { this.categoryId = categoryId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public PeriodType getPeriodType() { return periodType; }
    public void setPeriodType(PeriodType periodType) { this.periodType = periodType; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public BigDecimal getLimitAmount() { return limitAmount; }
    public void setLimitAmount(BigDecimal limitAmount) { this.limitAmount = limitAmount; }

    public boolean isRollover() { return rollover; }
    public void setRollover(boolean rollover) { this.rollover = rollover; }

    public BigDecimal getAlertThresholdPct() { return alertThresholdPct; }
    public void setAlertThresholdPct(BigDecimal alertThresholdPct) { this.alertThresholdPct = alertThresholdPct; }

    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
