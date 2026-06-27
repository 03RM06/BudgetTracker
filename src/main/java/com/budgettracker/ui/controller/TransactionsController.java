package com.budgettracker.ui.controller;

import com.budgettracker.domain.Account;
import com.budgettracker.domain.Transaction;
import com.budgettracker.domain.TxDirection;
import com.budgettracker.ui.AppContext;
import com.budgettracker.ui.Session;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import com.budgettracker.ui.ThemeManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class TransactionsController {

    /** Populated in the background alongside each transaction load; id → account name. */
    private Map<Long, String> accountNameMap = new HashMap<>();

    @FXML private TableView<Transaction> txTable;
    @FXML private TableColumn<Transaction, String> colDate;
    @FXML private TableColumn<Transaction, String> colDescription;
    @FXML private TableColumn<Transaction, Object> colAmount;
    @FXML private TableColumn<Transaction, String> colDirection;
    @FXML private TableColumn<Transaction, String> colStatus;
    @FXML private TableColumn<Transaction, String> colAccount;
    @FXML private DatePicker fromDate;
    @FXML private DatePicker toDate;
    @FXML private ChoiceBox<String> directionFilter;
    @FXML private Label statusLabel;

    @FXML
    public void initialize() {
        colDate.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getOccurredOn() != null
                        ? cd.getValue().getOccurredOn().toString() : ""));
        colDescription.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getDescription() != null
                        ? cd.getValue().getDescription() : ""));
        colAmount.setCellValueFactory(cd ->
                new SimpleObjectProperty<>(cd.getValue().getAmount()));
        colDirection.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getDirection() != null
                        ? cd.getValue().getDirection().name() : ""));
        colStatus.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getStatus() != null
                        ? cd.getValue().getStatus().name() : ""));
        colAccount.setCellValueFactory(cd ->
                new SimpleStringProperty(
                        accountNameMap.getOrDefault(
                                cd.getValue().getAccountId(),
                                String.valueOf(cd.getValue().getAccountId()))));

        directionFilter.setItems(FXCollections.observableArrayList(
                "ALL", "INCOME", "EXPENSE", "TRANSFER"));
        directionFilter.setValue("ALL");

        loadTransactions();
    }

    /** Bundles transactions + account-name map so the account column shows names, not raw IDs. */
    private record TxLoadResult(List<Transaction> transactions, Map<Long, String> accountNames) {}

    private void loadTransactions() {
        long userId = Session.current().getUserId();
        Task<TxLoadResult> task = new Task<>() {
            @Override
            protected TxLoadResult call() {
                List<Transaction> txs =
                        AppContext.get().transactionService.getAllTransactions(userId);
                Map<Long, String> names =
                        AppContext.get().accountService.getActiveAccounts(userId)
                                .stream()
                                .collect(Collectors.toMap(Account::getId, Account::getName));
                return new TxLoadResult(txs, names);
            }
        };
        task.setOnSucceeded(e -> {
            TxLoadResult result = task.getValue();
            accountNameMap = result.accountNames();
            applyDirectionFilter(result.transactions());
            statusLabel.setText("");
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            statusLabel.setText("Failed to load transactions: " + (ex != null ? ex.getMessage() : "unknown"));
        });
        new Thread(task, "tx-load").start();
    }

    @FXML
    private void onFilter() {
        long userId = Session.current().getUserId();
        LocalDate from = fromDate.getValue();
        LocalDate to = toDate.getValue();

        if (from != null && to != null) {
            Task<List<Transaction>> task = new Task<>() {
                @Override
                protected List<Transaction> call() {
                    return AppContext.get().transactionService
                            .getTransactionsByDateRange(userId, from, to);
                }
            };
            task.setOnSucceeded(e -> applyDirectionFilter(task.getValue()));
            task.setOnFailed(e -> {
                Throwable ex = task.getException();
                statusLabel.setText("Filter failed: " + (ex != null ? ex.getMessage() : "unknown"));
            });
            new Thread(task, "tx-filter").start();
        } else {
            loadTransactions();
        }
    }

    @FXML
    private void onClearFilter() {
        fromDate.setValue(null);
        toDate.setValue(null);
        directionFilter.setValue("ALL");
        loadTransactions();
    }

    private void applyDirectionFilter(List<Transaction> list) {
        String dir = directionFilter.getValue();
        List<Transaction> filtered = "ALL".equals(dir) ? list
                : list.stream()
                      .filter(tx -> tx.getDirection() != null && tx.getDirection().name().equals(dir))
                      .toList();
        txTable.setItems(FXCollections.observableArrayList(filtered));
    }

    @FXML
    private void onAddTransaction() {
        openTransactionDialog(null);
    }

    @FXML
    private void onEditSelected() {
        Transaction selected = txTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a transaction to edit.");
            return;
        }
        openTransactionDialog(selected);
    }

    @FXML
    private void onDeleteSelected() {
        Transaction selected = txTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a transaction to delete.");
            return;
        }
        long userId = Session.current().getUserId();
        // H3: ownership check is inside deleteTransaction(id, userId)
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                AppContext.get().transactionService.deleteTransaction(selected.getId(), userId);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            statusLabel.setText("Transaction deleted.");
            loadTransactions();
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            statusLabel.setText("Delete failed: " + (ex != null ? ex.getMessage() : "unknown"));
        });
        new Thread(task, "tx-delete").start();
    }

    private void openTransactionDialog(Transaction existing) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TransactionDialog.fxml"));
            Parent root = loader.load();
            ThemeManager.applyTheme(root);
            Stage dialog = new Stage();
            dialog.setTitle(existing == null ? "Add Transaction" : "Edit Transaction");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setScene(new Scene(root, 460, 500));
            TransactionDialogController controller = loader.getController();
            if (existing != null) {
                controller.initForEdit(existing);
            }
            dialog.showAndWait();
            loadTransactions();
        } catch (Exception e) {
            statusLabel.setText("Could not open dialog: " + e.getMessage());
        }
    }
}
