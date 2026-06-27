package com.budgettracker.ui;

import javafx.scene.Parent;

public class ThemeManager {
    private static boolean dark = false;

    public static boolean isDark() { return dark; }

    public static void applyTheme(Parent root) {
        if (dark) {
            if (!root.getStyleClass().contains("dark"))
                root.getStyleClass().add("dark");
        } else {
            root.getStyleClass().remove("dark");
        }
    }

    public static void toggle(Parent root) {
        dark = !dark;
        applyTheme(root);
    }
}
