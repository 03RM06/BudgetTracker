package com.budgettracker.service;

import com.budgettracker.domain.BudgetTrackerException;
import com.budgettracker.domain.DuplicateResourceException;
import com.budgettracker.domain.InvalidTransactionException;
import com.budgettracker.domain.ResourceNotFoundException;
import com.budgettracker.domain.User;
import com.budgettracker.persistence.UserRepository;
import com.budgettracker.util.PasswordHasher;
import java.sql.SQLException;
import java.time.Instant;

public class UserService {

    private final UserRepository userRepo;

    public UserService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public User register(String email, String displayName, String plainPassword) {
        if (email == null || email.isBlank()) {
            throw new InvalidTransactionException("Email must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new InvalidTransactionException("Display name must not be blank");
        }
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new InvalidTransactionException("Password must not be blank");
        }
        try {
            if (userRepo.findByEmail(email).isPresent()) {
                throw new DuplicateResourceException("Email already registered: " + email);
            }
            User user = new User();
            user.setEmail(email);
            user.setDisplayName(displayName);
            user.setPasswordHash(PasswordHasher.hash(plainPassword));
            user.setBaseCurrency("PHP");
            user.setDefaultZoneId("Asia/Manila");
            Instant now = Instant.now();
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            return userRepo.save(user);
        } catch (DuplicateResourceException e) {
            throw e;
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to register user", e);
        }
    }

    public User login(String email, String plainPassword) {
        try {
            User user = userRepo.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("No account found for: " + email));
            if (!PasswordHasher.verify(plainPassword, user.getPasswordHash())) {
                throw new InvalidTransactionException("Invalid password");
            }
            return user;
        } catch (ResourceNotFoundException | InvalidTransactionException e) {
            throw e;
        } catch (SQLException e) {
            throw new BudgetTrackerException("Login failed", e);
        }
    }

    public User findById(long id) {
        try {
            return userRepo.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to find user", e);
        }
    }
}
