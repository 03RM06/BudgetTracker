package com.budgettracker.ui.controller;

import com.budgettracker.domain.Account;
import com.budgettracker.domain.Transaction;
import com.budgettracker.service.EnvelopeStatus;
import com.budgettracker.ui.AppContext;
import com.budgettracker.ui.Session;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class DashboardHomeController {

    @FXML private Label accountCountLabel;
    @FXML private Label totalBalanceLabel;
    @FXML private Label pendingCountLabel;
    @FXML private Label budgetAlertsLabel;
    @FXML private Label statusLabel;

    @FXML
    public void initialize() {
        loadSummary();
    }

    private void loadSummary() {
        long userId = Session.current().getUserId();

        Task<SummaryData> task = new Task<>() {
            @Override
            protected SummaryData call() {
                List<Account> accounts = AppContext.get().accountService.getActiveAccounts(userId);
                BigDecimal totalBalance = accounts.stream()
                        .map(Account::getCurrentBalance)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                List<Transaction> pending = AppContext.get().transactionService.getPendingTransactions(userId);
                List<EnvelopeStatus> envelopes = AppContext.get().budgetService.getAllEnvelopeStatuses(userId);
                return new SummaryData(accounts.size(), totalBalance, pending.size(), envelopes);
            }
        };

        task.setOnSucceeded(e -> {
            SummaryData data = task.getValue();
            accountCountLabel.setText(String.valueOf(data.accountCount));
            totalBalanceLabel.setText(String.format("%.2f", data.totalBalance));
            pendingCountLabel.setText(String.valueOf(data.pendingCount));
            displayBudgetAlerts(data.envelopes);
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            statusLabel.setText("Failed to load summary: " + (ex != null ? ex.getMessage() : "unknown error"));
        });

        new Thread(task, "dashboard-home-load").start();
    }

    /** M1: surface WARN and OVER envelopes in the alert label. */
    private void displayBudgetAlerts(List<EnvelopeStatus> envelopes) {
        List<EnvelopeStatus> warnings = envelopes.stream()
                .filter(s -> s.getStatus() == com.budgettracker.domain.BudgetStatus.WARN
                          || s.getStatus() == com.budgettracker.domain.BudgetStatus.OVER)
                .collect(Collectors.toList());

        if (warnings.isEmpty()) {
            budgetAlertsLabel.setText("All budgets are on track.");
            budgetAlertsLabel.getStyleClass().removeAll("alert-warn", "alert-over");
            return;
        }

        StringBuilder sb = new StringBuilder();
        boolean hasOver = false;
        boolean hasWarn = false;
        for (EnvelopeStatus s : warnings) {
            switch (s.getStatus()) {
                case OVER -> { sb.append("[OVER BUDGET] "); hasOver = true; }
                case WARN -> { sb.append("[NEAR LIMIT] ");  hasWarn = true; }
                default -> {}
            }
            sb.append(s.getEnvelope().getName())
              .append(": spent ")
              .append(String.format("%.2f", s.getSpent()))
              .append(" of ")
              .append(String.format("%.2f", s.getEffectiveLimit()))
              .append("\n");
        }

        budgetAlertsLabel.setText(sb.toString().trim());
        budgetAlertsLabel.getStyleClass().removeAll("alert-warn", "alert-over");
        if (hasOver) {
            budgetAlertsLabel.getStyleClass().add("alert-over");
        } else if (hasWarn) {
            budgetAlertsLabel.getStyleClass().add("alert-warn");
        }
    }

    private static class SummaryData {
        final int accountCount;
        final BigDecimal totalBalance;
        final int pendingCount;
        final List<EnvelopeStatus> envelopes;

        SummaryData(int accountCount, BigDecimal totalBalance, int pendingCount,
                    List<EnvelopeStatus> envelopes) {
            this.accountCount = accountCount;
            this.totalBalance = totalBalance;
            this.pendingCount = pendingCount;
            this.envelopes = envelopes;
        }
    }
}
