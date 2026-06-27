package com.budgettracker.ui;

import com.budgettracker.persistence.AccountRepository;
import com.budgettracker.persistence.BudgetEnvelopeRepository;
import com.budgettracker.persistence.CategoryRepository;
import com.budgettracker.persistence.JdbcTxRunner;
import com.budgettracker.persistence.RecurringTransactionRepository;
import com.budgettracker.persistence.Repositories;
import com.budgettracker.persistence.TransactionRepository;
import com.budgettracker.persistence.TxRunner;
import com.budgettracker.persistence.UserRepository;
import com.budgettracker.service.AccountService;
import com.budgettracker.service.BudgetService;
import com.budgettracker.service.CategoryService;
import com.budgettracker.service.RecurrenceService;
import com.budgettracker.service.RecurringTransactionService;
import com.budgettracker.service.TransactionService;
import com.budgettracker.service.UserService;

public final class AppContext {

    private static AppContext instance;

    public final UserService userService;
    public final AccountService accountService;
    public final TransactionService transactionService;
    public final CategoryService categoryService;
    public final BudgetService budgetService;
    public final RecurrenceService recurrenceService;
    public final RecurringTransactionService recurringTransactionService;

    private AppContext() {
        TxRunner txRunner = new JdbcTxRunner();

        UserRepository userRepo = Repositories.createUserRepository();
        AccountRepository accountRepo = Repositories.createAccountRepository();
        TransactionRepository txRepo = Repositories.createTransactionRepository();
        CategoryRepository categoryRepo = Repositories.createCategoryRepository();
        BudgetEnvelopeRepository envelopeRepo = Repositories.createBudgetEnvelopeRepository();
        RecurringTransactionRepository ruleRepo = Repositories.createRecurringTransactionRepository();

        this.userService = new UserService(txRunner, userRepo);
        this.accountService = new AccountService(txRunner, accountRepo, txRepo);
        this.categoryService = new CategoryService(txRunner, categoryRepo);
        this.budgetService = new BudgetService(txRunner, envelopeRepo, txRepo);
        this.transactionService = new TransactionService(txRunner, txRepo, accountService, budgetService);
        // RecurrenceService needs TransactionService to materialize pending transactions
        this.recurrenceService = new RecurrenceService(txRunner, ruleRepo, txRepo, transactionService);
        this.recurringTransactionService = new RecurringTransactionService(ruleRepo, txRunner);
    }

    public static synchronized AppContext get() {
        if (instance == null) instance = new AppContext();
        return instance;
    }

    public static synchronized void reset() {
        instance = null;
    }
}
