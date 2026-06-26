package com.budgettracker.domain;

public class BudgetTrackerException extends RuntimeException {

    public BudgetTrackerException(String message) {
        super(message);
    }

    public BudgetTrackerException(String message, Throwable cause) {
        super(message, cause);
    }
}
