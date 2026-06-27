package com.budgettracker.ui.controller;

import com.budgettracker.domain.Account;
import com.budgettracker.ui.AppContext;
import com.budgettracker.ui.Session;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import com.budgettracker.ui.ThemeManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class AccountsController {

    @FXML private TableView<Account> accountTable;
    @FXML private TableColumn<Account, String>  colName;
    @FXML private TableColumn<Account, String>  colCurrency;
    @FXML private TableColumn<Account, Object>  colBalance;
    @FXML private TableColumn<Account, Object>  colType;
    @FXML private TableColumn<Account, Boolean> colArchived;
    @FXML private Button addButton;
    @FXML private Label statusLabel;

    @FXML
    public void initialize() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCurrency.setCellValueFactory(new PropertyValueFactory<>("currency"));
        colBalance.setCellValueFactory(new PropertyValueFactory<>("currentBalance"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colArchived.setCellValueFactory(new PropertyValueFactory<>("archived"));
        loadAccounts();
    }

    private void loadAccounts() {
        long userId = Session.current().getUserId();
        Task<List<Account>> task = new Task<>() {
            @Override
            protected List<Account> call() {
                // H3: scoped by userId — only this user's accounts are returned
                return AppContext.get().accountService.getActiveAccounts(userId);
            }
        };
        task.setOnSucceeded(e -> {
            accountTable.setItems(FXCollections.observableArrayList(task.getValue()));
            statusLabel.setText("");
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            statusLabel.setText("Failed to load accounts: " + (ex != null ? ex.getMessage() : "unknown"));
        });
        new Thread(task, "accounts-load").start();
    }

    @FXML
    private void onAddAccount() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AccountDialog.fxml"));
            Parent root = loader.load();
            ThemeManager.applyTheme(root);
            Stage dialog = new Stage();
            dialog.setTitle("Add Account");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setScene(new Scene(root, 400, 340));
            dialog.showAndWait();
            loadAccounts(); // refresh after dialog closes
        } catch (Exception e) {
            statusLabel.setText("Could not open dialog: " + e.getMessage());
        }
    }

    @FXML
    private void onArchiveSelected() {
        Account selected = accountTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select an account to archive.");
            return;
        }
        long userId = Session.current().getUserId();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                // H3: ownership check inside archiveAccount(id, userId)
                AppContext.get().accountService.archiveAccount(selected.getId(), userId);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            statusLabel.setText("Account archived.");
            loadAccounts();
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            statusLabel.setText("Archive failed: " + (ex != null ? ex.getMessage() : "unknown"));
        });
        new Thread(task, "account-archive").start();
    }
}
