package com.budgettracker.ui.controller;

import com.budgettracker.domain.Account;
import com.budgettracker.domain.Category;
import com.budgettracker.domain.CategoryType;
import com.budgettracker.domain.IncomeNature;
import com.budgettracker.domain.Transaction;
import com.budgettracker.domain.TxDirection;
import com.budgettracker.domain.TxStatus;
import com.budgettracker.ui.AppContext;
import com.budgettracker.ui.Session;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class TransactionDialogController {

    @FXML private Label titleLabel;
    @FXML private TextField amountField;
    @FXML private TextField descriptionField;
    @FXML private DatePicker datePicker;
    @FXML private ChoiceBox<TxDirection> directionChoice;
    @FXML private ChoiceBox<Account> accountChoice;
    @FXML private Label categoryLabel;
    @FXML private ChoiceBox<Category> categoryChoice;
    @FXML private Label transferLabel;
    @FXML private ChoiceBox<Account> transferAccountChoice;
    @FXML private Label exchangeRateLabel;
    @FXML private TextField exchangeRateField;
    @FXML private Label incomeNatureLabel;
    @FXML private ChoiceBox<IncomeNature> incomeNatureChoice;
    @FXML private Label errorLabel;

    private Transaction editingTransaction;
    private List<Account> allAccounts;

    @FXML
    public void initialize() {
        directionChoice.setItems(FXCollections.observableArrayList(TxDirection.values()));
        directionChoice.setValue(TxDirection.EXPENSE);
        incomeNatureChoice.setItems(FXCollections.observableArrayList(IncomeNature.values()));
        datePicker.setValue(LocalDate.now());

        StringConverter<Account> accountConverter = new StringConverter<>() {
            @Override public String toString(Account a) { return a == null ? "" : a.getName() + " (" + a.getCurrency() + ")"; }
            @Override public Account fromString(String s) { return null; }
        };
        accountChoice.setConverter(accountConverter);
        transferAccountChoice.setConverter(accountConverter);

        StringConverter<Category> catConverter = new StringConverter<>() {
            @Override public String toString(Category c) { return c == null ? "" : c.getName(); }
            @Override public Category fromString(String s) { return null; }
        };
        categoryChoice.setConverter(catConverter);

        loadChoiceBoxData();
        updateFieldVisibility(TxDirection.EXPENSE);
    }

    private void loadChoiceBoxData() {
        long userId = Session.current().getUserId();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                allAccounts = AppContext.get().accountService.getActiveAccounts(userId);
                List<Category> expenseCategories = AppContext.get().categoryService
                        .getCategoriesByType(userId, CategoryType.EXPENSE);
                javafx.application.Platform.runLater(() -> {
                    accountChoice.setItems(FXCollections.observableArrayList(allAccounts));
                    transferAccountChoice.setItems(FXCollections.observableArrayList(allAccounts));
                    categoryChoice.setItems(FXCollections.observableArrayList(expenseCategories));
                    if (!allAccounts.isEmpty()) accountChoice.setValue(allAccounts.get(0));
                });
                return null;
            }
        };
        new Thread(task, "tx-dialog-load").start();
    }

    /** Called by TransactionsController when opening edit mode (M3). */
    public void initForEdit(Transaction tx) {
        this.editingTransaction = tx;
        titleLabel.setText("Edit Transaction");
        amountField.setText(tx.getAmount() != null ? tx.getAmount().toPlainString() : "");
        descriptionField.setText(tx.getDescription() != null ? tx.getDescription() : "");
        datePicker.setValue(tx.getOccurredOn());
        if (tx.getDirection() != null) {
            directionChoice.setValue(tx.getDirection());
            updateFieldVisibility(tx.getDirection());
        }
        if (tx.getIncomeNature() != null) {
            incomeNatureChoice.setValue(tx.getIncomeNature());
        }
        // Account + category selection is deferred until choice boxes are populated
    }

    @FXML
    private void onDirectionChanged() {
        TxDirection dir = directionChoice.getValue();
        if (dir != null) updateFieldVisibility(dir);
    }

    private void updateFieldVisibility(TxDirection dir) {
        boolean isExpense  = dir == TxDirection.EXPENSE;
        boolean isTransfer = dir == TxDirection.TRANSFER;
        boolean isIncome   = dir == TxDirection.INCOME;

        categoryLabel.setVisible(isExpense);
        categoryLabel.setManaged(isExpense);
        categoryChoice.setVisible(isExpense);
        categoryChoice.setManaged(isExpense);

        transferLabel.setVisible(isTransfer);
        transferLabel.setManaged(isTransfer);
        transferAccountChoice.setVisible(isTransfer);
        transferAccountChoice.setManaged(isTransfer);
        exchangeRateLabel.setVisible(isTransfer);
        exchangeRateLabel.setManaged(isTransfer);
        exchangeRateField.setVisible(isTransfer);
        exchangeRateField.setManaged(isTransfer);

        incomeNatureLabel.setVisible(isIncome);
        incomeNatureLabel.setManaged(isIncome);
        incomeNatureChoice.setVisible(isIncome);
        incomeNatureChoice.setManaged(isIncome);
    }

    @FXML
    private void onSave() {
        errorLabel.setText("");

        // H5: Validate amount
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountField.getText().trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                errorLabel.setText("Amount must be greater than 0.");
                return;
            }
        } catch (NumberFormatException e) {
            errorLabel.setText("Amount must be a valid number.");
            return;
        }

        // H5: Validate date
        LocalDate date = datePicker.getValue();
        if (date == null) {
            errorLabel.setText("Date is required.");
            return;
        }

        // H5: Validate account
        Account account = accountChoice.getValue();
        if (account == null) {
            errorLabel.setText("Account is required.");
            return;
        }

        TxDirection direction = directionChoice.getValue();
        if (direction == null) {
            errorLabel.setText("Direction is required.");
            return;
        }

        // H5: Category required for EXPENSE
        Category category = null;
        if (direction == TxDirection.EXPENSE) {
            category = categoryChoice.getValue();
            if (category == null) {
                errorLabel.setText("Category is required for expense transactions.");
                return;
            }
        }

        // H5: Income nature required for INCOME
        IncomeNature incomeNature = null;
        if (direction == TxDirection.INCOME) {
            incomeNature = incomeNatureChoice.getValue();
            if (incomeNature == null) {
                errorLabel.setText("Income nature is required for income transactions.");
                return;
            }
        }

        // H5: Transfer destination must differ from source
        Account transferAccount = null;
        BigDecimal exchangeRate = null;
        if (direction == TxDirection.TRANSFER) {
            transferAccount = transferAccountChoice.getValue();
            if (transferAccount == null) {
                errorLabel.setText("Transfer destination account is required.");
                return;
            }
            if (transferAccount.getId() == account.getId()) {
                errorLabel.setText("Transfer destination must differ from source account.");
                return;
            }
            String rateText = exchangeRateField.getText().trim();
            if (!rateText.isEmpty()) {
                try {
                    exchangeRate = new BigDecimal(rateText);
                    if (exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
                        errorLabel.setText("Exchange rate must be positive.");
                        return;
                    }
                } catch (NumberFormatException e) {
                    errorLabel.setText("Exchange rate must be a valid number.");
                    return;
                }
            }
        }

        final Category finalCategory = category;
        final Account finalTransferAccount = transferAccount;
        final BigDecimal finalExchangeRate = exchangeRate;
        final IncomeNature finalIncomeNature = incomeNature;
        final long userId = Session.current().getUserId();

        if (editingTransaction == null) {
            // Add mode
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() {
                    Transaction tx = new Transaction();
                    tx.setUserId(userId);
                    tx.setAccountId(account.getId());
                    tx.setAmount(amount);
                    tx.setDirection(direction);
                    tx.setOccurredOn(date);
                    tx.setCurrency(account.getCurrency());
                    tx.setDescription(descriptionField.getText().trim());
                    tx.setStatus(TxStatus.POSTED);
                    tx.setIncomeNature(finalIncomeNature);
                    if (finalCategory != null) tx.setCategoryId(finalCategory.getId());
                    if (finalTransferAccount != null) tx.setTransferAccountId(finalTransferAccount.getId());
                    if (finalExchangeRate != null) tx.setExchangeRate(finalExchangeRate);
                    Instant now = Instant.now();
                    tx.setOccurredAt(now);
                    tx.setCreatedAt(now);
                    tx.setUpdatedAt(now);
                    AppContext.get().transactionService.addTransaction(tx);
                    return null;
                }
            };
            task.setOnSucceeded(e -> closeDialog());
            task.setOnFailed(e -> {
                Throwable ex = task.getException();
                errorLabel.setText(ex != null ? ex.getMessage() : "Failed to save transaction.");
            });
            new Thread(task, "tx-save").start();
        } else {
            // Edit mode (M3)
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() {
                    editingTransaction.setAccountId(account.getId());
                    editingTransaction.setAmount(amount);
                    editingTransaction.setDirection(direction);
                    editingTransaction.setOccurredOn(date);
                    editingTransaction.setCurrency(account.getCurrency());
                    editingTransaction.setDescription(descriptionField.getText().trim());
                    editingTransaction.setIncomeNature(finalIncomeNature);
                    editingTransaction.setCategoryId(finalCategory != null ? finalCategory.getId() : null);
                    editingTransaction.setTransferAccountId(
                            finalTransferAccount != null ? finalTransferAccount.getId() : null);
                    editingTransaction.setExchangeRate(finalExchangeRate);
                    editingTransaction.setUpdatedAt(Instant.now());
                    // H3: updateTransaction checks old.userId == updated.userId internally
                    AppContext.get().transactionService.updateTransaction(editingTransaction);
                    return null;
                }
            };
            task.setOnSucceeded(e -> closeDialog());
            task.setOnFailed(e -> {
                Throwable ex = task.getException();
                errorLabel.setText(ex != null ? ex.getMessage() : "Failed to update transaction.");
            });
            new Thread(task, "tx-update").start();
        }
    }

    @FXML
    private void onCancel() {
        closeDialog();
    }

    private void closeDialog() {
        ((Stage) amountField.getScene().getWindow()).close();
    }
}
