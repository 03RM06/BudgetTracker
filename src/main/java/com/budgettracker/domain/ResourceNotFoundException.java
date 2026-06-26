package com.budgettracker.domain;

public class ResourceNotFoundException extends BudgetTrackerException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
