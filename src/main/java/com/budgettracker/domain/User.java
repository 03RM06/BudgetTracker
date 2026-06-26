package com.budgettracker.domain;

import java.time.Instant;

public class User {

    private long id;
    private String email;
    private String displayName;
    private String passwordHash;
    private String baseCurrency;
    private String defaultZoneId;
    private Instant createdAt;
    private Instant updatedAt;

    public User() {
        this.baseCurrency = "PHP";
        this.defaultZoneId = "Asia/Manila";
    }

    public User(long id, String email, String displayName, String passwordHash,
                String baseCurrency, String defaultZoneId,
                Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.baseCurrency = baseCurrency;
        this.defaultZoneId = defaultZoneId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getBaseCurrency() { return baseCurrency; }
    public void setBaseCurrency(String baseCurrency) { this.baseCurrency = baseCurrency; }

    public String getDefaultZoneId() { return defaultZoneId; }
    public void setDefaultZoneId(String defaultZoneId) { this.defaultZoneId = defaultZoneId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
