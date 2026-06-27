package com.budgettracker.ui.controller;

import com.budgettracker.domain.User;
import com.budgettracker.ui.AppContext;
import com.budgettracker.ui.Session;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {

    @FXML private Label modeLabel;
    @FXML private TextField emailField;
    @FXML private TextField displayNameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label errorLabel;
    @FXML private Button primaryButton;
    @FXML private Hyperlink toggleLink;

    private boolean isRegisterMode = false;

    @FXML
    private void onPrimary() {
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        errorLabel.setText("");

        // H5: input validation
        if (email.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Email and password are required.");
            return;
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            errorLabel.setText("Please enter a valid email address.");
            return;
        }
        if (isRegisterMode) {
            String displayName = displayNameField.getText().trim();
            if (displayName.isEmpty()) {
                errorLabel.setText("Display name is required.");
                return;
            }
            if (password.length() < 8) {
                errorLabel.setText("Password must be at least 8 characters.");
                return;
            }
            if (!password.equals(confirmPasswordField.getText())) {
                errorLabel.setText("Passwords do not match.");
                return;
            }
        }

        try {
            User user;
            if (isRegisterMode) {
                String displayName = displayNameField.getText().trim();
                user = AppContext.get().userService.register(email, displayName, password);
            } else {
                user = AppContext.get().userService.login(email, password);
            }
            Session.start(user);
            navigateToDashboard();
        } catch (Exception e) {
            errorLabel.setText(e.getMessage() != null ? e.getMessage() : "Authentication failed.");
        }
    }

    @FXML
    private void onToggleMode() {
        isRegisterMode = !isRegisterMode;
        modeLabel.setText(isRegisterMode ? "Register" : "Sign In");
        primaryButton.setText(isRegisterMode ? "Create Account" : "Sign In");
        toggleLink.setText(isRegisterMode
                ? "Already have an account? Sign In"
                : "Don't have an account? Register");
        displayNameField.setManaged(isRegisterMode);
        displayNameField.setVisible(isRegisterMode);
        confirmPasswordField.setManaged(isRegisterMode);
        confirmPasswordField.setVisible(isRegisterMode);
        errorLabel.setText("");
    }

    private void navigateToDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Dashboard.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) emailField.getScene().getWindow();
            Scene scene = new Scene(root, 1100, 700);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            stage.setScene(scene);
        } catch (Exception e) {
            errorLabel.setText("Failed to load dashboard: " + e.getMessage());
        }
    }
}
