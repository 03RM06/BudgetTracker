package com.budgettracker.ui;

import com.budgettracker.domain.User;

public final class Session {

    private static Session current;

    private final long userId;
    private final String email;
    private final String displayName;

    private Session(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.displayName = user.getDisplayName() != null ? user.getDisplayName() : user.getEmail();
    }

    public static void start(User user) {
        current = new Session(user);
    }

    public static void clear() {
        current = null;
    }

    public static Session current() {
        if (current == null) throw new IllegalStateException("No active session");
        return current;
    }

    public static boolean isActive() {
        return current != null;
    }

    public long getUserId() { return userId; }

    public String getEmail() { return email; }

    public String getDisplayName() { return displayName; }
}
