package com.budgettracker.ui.controller;

import com.budgettracker.domain.Transaction;
import com.budgettracker.ui.AppContext;
import com.budgettracker.ui.Session;
import java.math.BigDecimal;
import java.util.List;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class PendingConfirmController {

    @FXML private TableView<Transaction> pendingTable;
    @FXML private TableColumn<Transaction, String> colDate;
    @FXML private TableColumn<Transaction, String> colDesc;
    @FXML private TableColumn<Transaction, Object> colAmount;
    @FXML private TextField actualAmountField;
    @FXML private Label statusLabel;

    @FXML
    public void initialize() {
        colDate.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getOccurredOn() != null
                        ? cd.getValue().getOccurredOn().toString() : ""));
        colDesc.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getDescription() != null
                        ? cd.getValue().getDescription() : ""));
        colAmount.setCellValueFactory(cd ->
                new SimpleObjectProperty<>(cd.getValue().getAmount()));
        loadPending();
    }

    private void loadPending() {
        long userId = Session.current().getUserId();
        Task<List<Transaction>> task = new Task<>() {
            @Override
            protected List<Transaction> call() {
                return AppContext.get().transactionService.getPendingTransactions(userId);
            }
        };
        task.setOnSucceeded(e ->
                pendingTable.setItems(FXCollections.observableArrayList(task.getValue())));
        task.setOnFailed(e -> statusLabel.setText("Failed to load pending transactions."));
        new Thread(task, "pending-load").start();
    }

    @FXML
    private void onConfirm() {
        Transaction selected = pendingTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a transaction to confirm.");
            return;
        }
        BigDecimal actualAmount;
        try {
            actualAmount = new BigDecimal(actualAmountField.getText().trim());
            if (actualAmount.compareTo(BigDecimal.ZERO) <= 0) {
                statusLabel.setText("Actual amount must be > 0.");
                return;
            }
        } catch (NumberFormatException e) {
            statusLabel.setText("Enter a valid amount.");
            return;
        }
        long userId = Session.current().getUserId();
        BigDecimal finalAmount = actualAmount;
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                // H3: confirmPending(txId, amount, userId) checks ownership
                AppContext.get().transactionService.confirmPending(
                        selected.getId(), finalAmount, userId);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            statusLabel.setText("Transaction confirmed.");
            actualAmountField.clear();
            loadPending();
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            statusLabel.setText("Confirm failed: " + (ex != null ? ex.getMessage() : "unknown"));
        });
        new Thread(task, "pending-confirm").start();
    }

    @FXML
    private void onCancelTx() {
        Transaction selected = pendingTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a transaction to cancel.");
            return;
        }
        long userId = Session.current().getUserId();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                // H3: cancelPending(txId, userId) checks ownership
                AppContext.get().transactionService.cancelPending(selected.getId(), userId);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            statusLabel.setText("Transaction cancelled.");
            loadPending();
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            statusLabel.setText("Cancel failed: " + (ex != null ? ex.getMessage() : "unknown"));
        });
        new Thread(task, "pending-cancel").start();
    }

    @FXML
    private void onClose() {
        ((Stage) pendingTable.getScene().getWindow()).close();
    }
}
