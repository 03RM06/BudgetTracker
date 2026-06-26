package com.budgettracker.domain;

public class InvalidTransactionException extends BudgetTrackerException {

    public InvalidTransactionException(String message) {
        super(message);
    }

    public InvalidTransactionException(String message, Throwable cause) {
        super(message, cause);
    }
}
