package com.budgettracker.ui;

import javafx.application.Application;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("BudgetTracker");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
