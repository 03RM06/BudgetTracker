package com.budgettracker.persistence;

public final class Repositories {
    private Repositories() {}

    public static UserRepository createUserRepository() { return new JdbcUserRepository(); }
    public static AccountRepository createAccountRepository() { return new JdbcAccountRepository(); }
    public static TransactionRepository createTransactionRepository() { return new JdbcTransactionRepository(); }
    public static CategoryRepository createCategoryRepository() { return new JdbcCategoryRepository(); }
    public static BudgetEnvelopeRepository createBudgetEnvelopeRepository() { return new JdbcBudgetEnvelopeRepository(); }
    public static RecurringTransactionRepository createRecurringTransactionRepository() { return new JdbcRecurringTransactionRepository(); }
}
