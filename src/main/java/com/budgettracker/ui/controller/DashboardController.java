package com.budgettracker.ui.controller;

import com.budgettracker.ui.AppContext;
import com.budgettracker.ui.Session;
import com.budgettracker.ui.ThemeManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class DashboardController {

    @FXML private StackPane contentArea;
    @FXML private Label userLabel;
    @FXML private Button themeToggleBtn;
    @FXML private Button btnHome;
    @FXML private Button btnAccounts;
    @FXML private Button btnTransactions;
    @FXML private Button btnBudgets;
    @FXML private Button btnRecurring;

    @FXML
    public void initialize() {
        userLabel.setText(Session.current().getDisplayName());
        // Materialize any due recurring transactions on app start (background thread)
        new Thread(() -> {
            try {
                AppContext.get().recurrenceService.materializeDue(Session.current().getUserId());
            } catch (Exception ignored) {
                // best-effort; don't crash the UI on startup
            }
        }, "recurrence-materialize").start();
        showHome();
    }

    @FXML private void showHome()         { loadCenter("/fxml/DashboardHome.fxml", btnHome); }
    @FXML private void showAccounts()     { loadCenter("/fxml/Accounts.fxml", btnAccounts); }
    @FXML private void showTransactions() { loadCenter("/fxml/Transactions.fxml", btnTransactions); }
    @FXML private void showBudgets()      { loadCenter("/fxml/Budgets.fxml", btnBudgets); }
    @FXML private void showRecurring()    { loadCenter("/fxml/Recurring.fxml", btnRecurring); }

    @FXML
    private void onLogout() {
        Session.clear();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) contentArea.getScene().getWindow();
            Scene scene = new Scene(root, 900, 600);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            stage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onToggleTheme() {
        ThemeManager.toggle(themeToggleBtn.getScene().getRoot());
        themeToggleBtn.setText(ThemeManager.isDark() ? "☾ Dark Mode" : "☀ Light Mode");
    }

    private void loadCenter(String fxmlPath, Button activeBtn) {
        try {
            for (Button b : new Button[]{btnHome, btnAccounts, btnTransactions, btnBudgets, btnRecurring}) {
                b.getStyleClass().remove("active");
            }
            if (activeBtn != null) activeBtn.getStyleClass().add("active");
            Parent view = FXMLLoader.load(getClass().getResource(fxmlPath));
            contentArea.getChildren().setAll(view);
        } catch (Exception e) {
            contentArea.getChildren().setAll(new Label("Failed to load view: " + e.getMessage()));
        }
    }
}
