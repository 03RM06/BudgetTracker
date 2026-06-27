package com.budgettracker.ui.controller;

import com.budgettracker.domain.Account;
import com.budgettracker.domain.Category;
import com.budgettracker.domain.CategoryType;
import com.budgettracker.domain.Frequency;
import com.budgettracker.domain.IncomeNature;
import com.budgettracker.domain.RecurringTransaction;
import com.budgettracker.domain.TxDirection;
import com.budgettracker.domain.TxStatus;
import com.budgettracker.ui.AppContext;
import com.budgettracker.ui.Session;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class RecurringDialogController {

    @FXML private TextField descriptionField;
    @FXML private TextField amountField;
    @FXML private ChoiceBox<TxDirection> directionChoice;
    @FXML private ChoiceBox<Account> accountChoice;
    @FXML private Label categoryLabel;
    @FXML private ChoiceBox<Category> categoryChoice;
    @FXML private Label incomeNatureLabel;
    @FXML private ChoiceBox<IncomeNature> incomeNatureChoice;
    @FXML private ChoiceBox<Frequency> frequencyChoice;
    @FXML private DatePicker startDatePicker;
    @FXML private TextField zoneField;
    @FXML private CheckBox variableAmountCheck;
    @FXML private Label errorLabel;

    @FXML
    public void initialize() {
        directionChoice.setItems(FXCollections.observableArrayList(TxDirection.values()));
        directionChoice.setValue(TxDirection.EXPENSE);
        frequencyChoice.setItems(FXCollections.observableArrayList(Frequency.values()));
        frequencyChoice.setValue(Frequency.MONTHLY);
        incomeNatureChoice.setItems(FXCollections.observableArrayList(IncomeNature.values()));
        startDatePicker.setValue(LocalDate.now());
        zoneField.setText(ZoneId.systemDefault().getId());

        accountChoice.setConverter(new StringConverter<>() {
            @Override public String toString(Account a) { return a == null ? "" : a.getName(); }
            @Override public Account fromString(String s) { return null; }
        });
        categoryChoice.setConverter(new StringConverter<>() {
            @Override public String toString(Category c) { return c == null ? "" : c.getName(); }
            @Override public Category fromString(String s) { return null; }
        });

        updateFieldVisibility(TxDirection.EXPENSE);
        loadChoiceBoxData();
    }

    private void loadChoiceBoxData() {
        long userId = Session.current().getUserId();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                List<Account> accounts = AppContext.get().accountService.getActiveAccounts(userId);
                List<Category> expCats = AppContext.get().categoryService
                        .getCategoriesByType(userId, CategoryType.EXPENSE);
                javafx.application.Platform.runLater(() -> {
                    accountChoice.setItems(FXCollections.observableArrayList(accounts));
                    categoryChoice.setItems(FXCollections.observableArrayList(expCats));
                    if (!accounts.isEmpty()) accountChoice.setValue(accounts.get(0));
                });
                return null;
            }
        };
        new Thread(task, "recurring-dialog-load").start();
    }

    @FXML
    private void onDirectionChanged() {
        TxDirection dir = directionChoice.getValue();
        if (dir != null) updateFieldVisibility(dir);
    }

    private void updateFieldVisibility(TxDirection dir) {
        boolean isExpense = dir == TxDirection.EXPENSE;
        boolean isIncome  = dir == TxDirection.INCOME;
        categoryLabel.setVisible(isExpense);
        categoryLabel.setManaged(isExpense);
        categoryChoice.setVisible(isExpense);
        categoryChoice.setManaged(isExpense);
        incomeNatureLabel.setVisible(isIncome);
        incomeNatureLabel.setManaged(isIncome);
        incomeNatureChoice.setVisible(isIncome);
        incomeNatureChoice.setManaged(isIncome);
    }

    @FXML
    private void onCreate() {
        errorLabel.setText("");

        String description = descriptionField.getText().trim();
        if (description.isEmpty()) {
            errorLabel.setText("Description is required.");
            return;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountField.getText().trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                errorLabel.setText("Amount must be > 0.");
                return;
            }
        } catch (NumberFormatException e) {
            errorLabel.setText("Amount must be a valid number.");
            return;
        }
        TxDirection direction = directionChoice.getValue();
        if (direction == null) {
            errorLabel.setText("Direction is required.");
            return;
        }
        Account account = accountChoice.getValue();
        if (account == null) {
            errorLabel.setText("Account is required.");
            return;
        }
        Category category = null;
        if (direction == TxDirection.EXPENSE) {
            category = categoryChoice.getValue();
            if (category == null) {
                errorLabel.setText("Category is required for expense rules.");
                return;
            }
        }
        IncomeNature incomeNature = null;
        if (direction == TxDirection.INCOME) {
            incomeNature = incomeNatureChoice.getValue();
            if (incomeNature == null) {
                errorLabel.setText("Income nature is required for income rules.");
                return;
            }
        }
        Frequency frequency = frequencyChoice.getValue();
        if (frequency == null) {
            errorLabel.setText("Frequency is required.");
            return;
        }
        LocalDate startDate = startDatePicker.getValue();
        if (startDate == null) {
            errorLabel.setText("Start date is required.");
            return;
        }
        String zoneId = zoneField.getText().trim();
        try {
            ZoneId.of(zoneId);
        } catch (Exception e) {
            errorLabel.setText("Invalid zone ID. Use a valid IANA zone (e.g. Asia/Manila).");
            return;
        }

        final Category finalCategory = category;
        final IncomeNature finalIncomeNature = incomeNature;
        final long userId = Session.current().getUserId();
        final boolean variableAmount = variableAmountCheck.isSelected();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                RecurringTransaction rule = new RecurringTransaction();
                rule.setUserId(userId);
                rule.setAccountId(account.getId());
                rule.setDirection(direction);
                rule.setTemplateAmount(amount);
                rule.setVariableAmount(variableAmount);
                rule.setIncomeNature(finalIncomeNature);
                rule.setCurrency(account.getCurrency());
                rule.setFrequency(frequency);
                rule.setIntervalCount(1);
                rule.setAnchorDate(startDate);
                rule.setZoneId(zoneId);
                rule.setActive(true);
                rule.setDescription(description);
                if (finalCategory != null) rule.setCategoryId(finalCategory.getId());
                Instant now = Instant.now();
                rule.setCreatedAt(now);
                rule.setUpdatedAt(now);
                AppContext.get().recurringTransactionService.createRule(rule);
                return null;
            }
        };
        task.setOnSucceeded(e -> closeDialog());
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            errorLabel.setText(ex != null ? ex.getMessage() : "Failed to create rule.");
        });
        new Thread(task, "recurring-create").start();
    }

    @FXML
    private void onCancel() {
        closeDialog();
    }

    private void closeDialog() {
        ((Stage) descriptionField.getScene().getWindow()).close();
    }
}
