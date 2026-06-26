package com.budgettracker.domain;

public class DuplicateResourceException extends BudgetTrackerException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
