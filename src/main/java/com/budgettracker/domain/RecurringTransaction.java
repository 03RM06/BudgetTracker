package com.budgettracker.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class RecurringTransaction {

    private long id;
    private long userId;
    private long accountId;
    private Long categoryId;
    private TxDirection direction;
    private BigDecimal templateAmount;
    private boolean variableAmount;
    private IncomeNature incomeNature;
    private String currency;
    private Frequency frequency;
    private int intervalCount;
    private LocalDate anchorDate;
    private String zoneId;
    private Integer dayOfMonth;
    private LocalDate nextRunDate;
    private LocalDate endDate;
    private Integer maxOccurrences;
    private boolean active;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;

    public RecurringTransaction() {}

    public RecurringTransaction(long id, long userId, long accountId, Long categoryId,
                                TxDirection direction, BigDecimal templateAmount,
                                boolean variableAmount, IncomeNature incomeNature,
                                String currency, Frequency frequency, int intervalCount,
                                LocalDate anchorDate, String zoneId, Integer dayOfMonth,
                                LocalDate nextRunDate, LocalDate endDate,
                                Integer maxOccurrences, boolean active, String description,
                                Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.direction = direction;
        this.templateAmount = templateAmount;
        this.variableAmount = variableAmount;
        this.incomeNature = incomeNature;
        this.currency = currency;
        this.frequency = frequency;
        this.intervalCount = intervalCount;
        this.anchorDate = anchorDate;
        this.zoneId = zoneId;
        this.dayOfMonth = dayOfMonth;
        this.nextRunDate = nextRunDate;
        this.endDate = endDate;
        this.maxOccurrences = maxOccurrences;
        this.active = active;
        this.description = description;
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

    public BigDecimal getTemplateAmount() { return templateAmount; }
    public void setTemplateAmount(BigDecimal templateAmount) { this.templateAmount = templateAmount; }

    public boolean isVariableAmount() { return variableAmount; }
    public void setVariableAmount(boolean variableAmount) { this.variableAmount = variableAmount; }

    public IncomeNature getIncomeNature() { return incomeNature; }
    public void setIncomeNature(IncomeNature incomeNature) { this.incomeNature = incomeNature; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Frequency getFrequency() { return frequency; }
    public void setFrequency(Frequency frequency) { this.frequency = frequency; }

    public int getIntervalCount() { return intervalCount; }
    public void setIntervalCount(int intervalCount) { this.intervalCount = intervalCount; }

    public LocalDate getAnchorDate() { return anchorDate; }
    public void setAnchorDate(LocalDate anchorDate) { this.anchorDate = anchorDate; }

    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }

    public Integer getDayOfMonth() { return dayOfMonth; }
    public void setDayOfMonth(Integer dayOfMonth) { this.dayOfMonth = dayOfMonth; }

    public LocalDate getNextRunDate() { return nextRunDate; }
    public void setNextRunDate(LocalDate nextRunDate) { this.nextRunDate = nextRunDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Integer getMaxOccurrences() { return maxOccurrences; }
    public void setMaxOccurrences(Integer maxOccurrences) { this.maxOccurrences = maxOccurrences; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
