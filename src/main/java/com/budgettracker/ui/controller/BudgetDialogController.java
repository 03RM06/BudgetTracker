package com.budgettracker.ui.controller;

import com.budgettracker.domain.Category;
import com.budgettracker.domain.CategoryType;
import com.budgettracker.domain.PeriodType;
import com.budgettracker.ui.AppContext;
import com.budgettracker.ui.Session;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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

public class BudgetDialogController {

    @FXML private TextField nameField;
    @FXML private ChoiceBox<Category> categoryChoice;
    @FXML private ChoiceBox<PeriodType> periodTypeChoice;
    @FXML private DatePicker periodStartPicker;
    @FXML private TextField limitField;
    @FXML private TextField alertThresholdField;
    @FXML private Label errorLabel;

    @FXML
    public void initialize() {
        periodTypeChoice.setItems(FXCollections.observableArrayList(
                PeriodType.WEEKLY, PeriodType.MONTHLY, PeriodType.YEARLY));
        periodTypeChoice.setValue(PeriodType.MONTHLY);
        periodStartPicker.setValue(LocalDate.now().withDayOfMonth(1));
        alertThresholdField.setText("80");

        categoryChoice.setConverter(new StringConverter<>() {
            @Override public String toString(Category c) { return c == null ? "" : c.getName(); }
            @Override public Category fromString(String s) { return null; }
        });

        long userId = Session.current().getUserId();
        Task<List<Category>> task = new Task<>() {
            @Override
            protected List<Category> call() {
                return AppContext.get().categoryService.getCategoriesByType(userId, CategoryType.EXPENSE);
            }
        };
        task.setOnSucceeded(e -> categoryChoice.setItems(
                FXCollections.observableArrayList(task.getValue())));
        task.setOnFailed(e -> errorLabel.setText("Failed to load categories."));
        new Thread(task, "budget-dialog-load").start();
    }

    @FXML
    private void onCreate() {
        errorLabel.setText("");

        // H5: input validation
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            errorLabel.setText("Envelope name is required.");
            return;
        }
        Category category = categoryChoice.getValue();
        if (category == null) {
            errorLabel.setText("Category is required.");
            return;
        }
        PeriodType periodType = periodTypeChoice.getValue();
        if (periodType == null) {
            errorLabel.setText("Period type is required.");
            return;
        }
        LocalDate periodStart = periodStartPicker.getValue();
        if (periodStart == null) {
            errorLabel.setText("Period start date is required.");
            return;
        }
        BigDecimal limit;
        try {
            limit = new BigDecimal(limitField.getText().trim());
            if (limit.compareTo(BigDecimal.ZERO) <= 0) {
                errorLabel.setText("Limit must be greater than 0.");
                return;
            }
        } catch (NumberFormatException e) {
            errorLabel.setText("Limit must be a valid number.");
            return;
        }
        BigDecimal alertThreshold;
        try {
            alertThreshold = new BigDecimal(alertThresholdField.getText().trim());
            if (alertThreshold.compareTo(BigDecimal.ZERO) < 0
                    || alertThreshold.compareTo(new BigDecimal("100")) > 0) {
                errorLabel.setText("Alert threshold must be between 0 and 100.");
                return;
            }
        } catch (NumberFormatException e) {
            errorLabel.setText("Alert threshold must be a valid number.");
            return;
        }

        long userId = Session.current().getUserId();
        String zoneId = ZoneId.systemDefault().getId();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                AppContext.get().budgetService.createEnvelope(
                        userId, category.getId(), name,
                        periodType, periodStart,
                        limit, false, alertThreshold, zoneId);
                return null;
            }
        };
        task.setOnSucceeded(e -> closeDialog());
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            errorLabel.setText(ex != null ? ex.getMessage() : "Failed to create envelope.");
        });
        new Thread(task, "budget-create").start();
    }

    @FXML
    private void onCancel() {
        closeDialog();
    }

    private void closeDialog() {
        ((Stage) nameField.getScene().getWindow()).close();
    }
}
