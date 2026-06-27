package com.budgettracker.ui.controller;

import com.budgettracker.domain.RecurringTransaction;
import com.budgettracker.domain.Transaction;
import com.budgettracker.ui.AppContext;
import com.budgettracker.ui.Session;
import java.util.List;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class RecurringController {

    @FXML private TableView<RecurringTransaction> ruleTable;
    @FXML private TableColumn<RecurringTransaction, String>  colDescription;
    @FXML private TableColumn<RecurringTransaction, String>  colFrequency;
    @FXML private TableColumn<RecurringTransaction, String>  colNextRun;
    @FXML private TableColumn<RecurringTransaction, String>  colZone;
    @FXML private TableColumn<RecurringTransaction, Boolean> colActive;
    @FXML private Button confirmPendingBtn;
    @FXML private Label statusLabel;

    @FXML
    public void initialize() {
        colDescription.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getDescription()));
        colFrequency.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getFrequency() != null
                        ? cd.getValue().getFrequency().name() : ""));
        colNextRun.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getNextRunDate() != null
                        ? cd.getValue().getNextRunDate().toString() : ""));
        colZone.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getZoneId()));
        colActive.setCellValueFactory(cd ->
                new SimpleBooleanProperty(cd.getValue().isActive()).asObject());
        loadRules();
    }

    private void loadRules() {
        long userId = Session.current().getUserId();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                List<RecurringTransaction> rules =
                        AppContext.get().recurringTransactionService.getActiveRules(userId);
                List<Transaction> pending =
                        AppContext.get().transactionService.getPendingTransactions(userId);
                javafx.application.Platform.runLater(() -> {
                    ruleTable.setItems(FXCollections.observableArrayList(rules));
                    boolean hasPending = !pending.isEmpty();
                    confirmPendingBtn.setVisible(hasPending);
                    confirmPendingBtn.setManaged(hasPending);
                });
                return null;
            }
        };
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            statusLabel.setText("Failed to load rules: " + (ex != null ? ex.getMessage() : "unknown"));
        });
        new Thread(task, "recurring-load").start();
    }

    @FXML
    private void onAddRule() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/RecurringDialog.fxml"));
            Stage dialog = new Stage();
            dialog.setTitle("Add Recurring Rule");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setScene(new Scene(loader.load(), 460, 540));
            dialog.showAndWait();
            loadRules();
        } catch (Exception e) {
            statusLabel.setText("Could not open dialog: " + e.getMessage());
        }
    }

    @FXML
    private void onConfirmPending() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PendingConfirmDialog.fxml"));
            Stage dialog = new Stage();
            dialog.setTitle("Confirm Pending Transactions");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setScene(new Scene(loader.load(), 500, 420));
            dialog.showAndWait();
            loadRules();
        } catch (Exception e) {
            statusLabel.setText("Could not open dialog: " + e.getMessage());
        }
    }

    @FXML
    private void onToggleActive() {
        RecurringTransaction selected = ruleTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a rule to enable or disable.");
            return;
        }
        long userId = Session.current().getUserId();
        boolean currentlyActive = selected.isActive();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                if (currentlyActive) {
                    AppContext.get().recurringTransactionService.disableRule(selected.getId(), userId);
                } else {
                    AppContext.get().recurringTransactionService.enableRule(selected.getId(), userId);
                }
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            statusLabel.setText(currentlyActive ? "Rule disabled." : "Rule enabled.");
            loadRules();
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            statusLabel.setText("Toggle failed: " + (ex != null ? ex.getMessage() : "unknown"));
        });
        new Thread(task, "recurring-toggle").start();
    }
}
