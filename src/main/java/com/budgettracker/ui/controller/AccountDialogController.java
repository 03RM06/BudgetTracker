package com.budgettracker.ui.controller;

import com.budgettracker.domain.AccountType;
import com.budgettracker.ui.AppContext;
import com.budgettracker.ui.Session;
import java.math.BigDecimal;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class AccountDialogController {

    @FXML private TextField nameField;
    @FXML private TextField currencyField;
    @FXML private ChoiceBox<AccountType> typeChoice;
    @FXML private TextField balanceField;
    @FXML private Label errorLabel;

    @FXML
    public void initialize() {
        typeChoice.setItems(FXCollections.observableArrayList(AccountType.values()));
        typeChoice.setValue(AccountType.BANK);
    }

    @FXML
    private void onCreate() {
        errorLabel.setText("");

        // H5: input validation
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            errorLabel.setText("Account name is required.");
            return;
        }
        String currency = currencyField.getText().trim().toUpperCase();
        if (!currency.matches("[A-Z]{3}")) {
            errorLabel.setText("Currency must be a 3-letter ISO code (e.g. PHP, USD).");
            return;
        }
        AccountType type = typeChoice.getValue();
        if (type == null) {
            errorLabel.setText("Please select an account type.");
            return;
        }
        BigDecimal balance;
        try {
            balance = new BigDecimal(balanceField.getText().trim());
            if (balance.compareTo(BigDecimal.ZERO) < 0) {
                errorLabel.setText("Opening balance must be >= 0.");
                return;
            }
        } catch (NumberFormatException ex) {
            errorLabel.setText("Opening balance must be a valid number.");
            return;
        }

        long userId = Session.current().getUserId();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                AppContext.get().accountService.createAccount(userId, name, type, balance, currency);
                return null;
            }
        };
        task.setOnSucceeded(e -> closeDialog());
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            errorLabel.setText(ex != null ? ex.getMessage() : "Failed to create account.");
        });
        new Thread(task, "account-create").start();
    }

    @FXML
    private void onCancel() {
        closeDialog();
    }

    private void closeDialog() {
        ((Stage) nameField.getScene().getWindow()).close();
    }
}
