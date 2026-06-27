package com.budgettracker.ui.controller;

import com.budgettracker.domain.BudgetStatus;
import com.budgettracker.service.EnvelopeStatus;
import com.budgettracker.ui.AppContext;
import com.budgettracker.ui.Session;
import java.util.List;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import com.budgettracker.ui.ThemeManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class BudgetsController {

    @FXML private TableView<EnvelopeStatus> budgetTable;
    @FXML private TableColumn<EnvelopeStatus, String> colName;
    @FXML private TableColumn<EnvelopeStatus, String> colPeriod;
    @FXML private TableColumn<EnvelopeStatus, Object> colLimit;
    @FXML private TableColumn<EnvelopeStatus, Object> colSpent;
    @FXML private TableColumn<EnvelopeStatus, Object> colRemaining;
    @FXML private TableColumn<EnvelopeStatus, BudgetStatus> colStatus;
    @FXML private Label statusLabel;

    @FXML
    public void initialize() {
        colName.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getEnvelope().getName()));
        colPeriod.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getEnvelope().getPeriodType().name()));
        colLimit.setCellValueFactory(cd ->
                new SimpleObjectProperty<>(cd.getValue().getEffectiveLimit()));
        colSpent.setCellValueFactory(cd ->
                new SimpleObjectProperty<>(cd.getValue().getSpent()));
        colRemaining.setCellValueFactory(cd ->
                new SimpleObjectProperty<>(cd.getValue().getRemaining()));
        colStatus.setCellValueFactory(cd ->
                new SimpleObjectProperty<>(cd.getValue().getStatus()));

        // M1: Color the status column by WARN/OVER
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BudgetStatus item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("alert-warn", "alert-over");
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.name());
                    if (item == BudgetStatus.WARN) getStyleClass().add("alert-warn");
                    else if (item == BudgetStatus.OVER) getStyleClass().add("alert-over");
                }
            }
        });

        loadEnvelopes();
    }

    private void loadEnvelopes() {
        long userId = Session.current().getUserId();
        Task<List<EnvelopeStatus>> task = new Task<>() {
            @Override
            protected List<EnvelopeStatus> call() {
                return AppContext.get().budgetService.getAllEnvelopeStatuses(userId);
            }
        };
        task.setOnSucceeded(e -> {
            budgetTable.setItems(FXCollections.observableArrayList(task.getValue()));
            statusLabel.setText("");
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            statusLabel.setText("Failed to load budgets: " + (ex != null ? ex.getMessage() : "unknown"));
        });
        new Thread(task, "budgets-load").start();
    }

    @FXML
    private void onAddEnvelope() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/BudgetDialog.fxml"));
            Parent root = loader.load();
            ThemeManager.applyTheme(root);
            Stage dialog = new Stage();
            dialog.setTitle("Add Budget Envelope");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setScene(new Scene(root, 400, 380));
            dialog.showAndWait();
            loadEnvelopes();
        } catch (Exception e) {
            statusLabel.setText("Could not open dialog: " + e.getMessage());
        }
    }

    @FXML
    private void onDeleteSelected() {
        EnvelopeStatus selected = budgetTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select an envelope to delete.");
            return;
        }
        long userId = Session.current().getUserId();
        long envelopeId = selected.getEnvelope().getId();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                // H3: ownership check inside deleteEnvelope(id, userId)
                AppContext.get().budgetService.deleteEnvelope(envelopeId, userId);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            statusLabel.setText("Envelope deleted.");
            loadEnvelopes();
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            statusLabel.setText("Delete failed: " + (ex != null ? ex.getMessage() : "unknown"));
        });
        new Thread(task, "budget-delete").start();
    }
}
