package com.budgettracker.service;

import com.budgettracker.domain.BudgetEnvelope;
import com.budgettracker.domain.BudgetStatus;
import java.math.BigDecimal;

public class EnvelopeStatus {

    private final BudgetEnvelope envelope;
    private final BigDecimal spent;
    private final BigDecimal effectiveLimit;
    private final BigDecimal remaining;
    private final BudgetStatus status;

    public EnvelopeStatus(BudgetEnvelope envelope, BigDecimal spent,
                          BigDecimal effectiveLimit, BigDecimal remaining,
                          BudgetStatus status) {
        this.envelope = envelope;
        this.spent = spent;
        this.effectiveLimit = effectiveLimit;
        this.remaining = remaining;
        this.status = status;
    }

    public BudgetEnvelope getEnvelope()        { return envelope; }
    public BigDecimal getSpent()               { return spent; }
    public BigDecimal getEffectiveLimit()      { return effectiveLimit; }
    public BigDecimal getRemaining()           { return remaining; }
    public BudgetStatus getStatus()            { return status; }
}
