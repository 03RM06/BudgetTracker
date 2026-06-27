package com.budgettracker.persistence;

import com.budgettracker.domain.User;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(long id);
    Optional<User> findByEmail(String email);
    List<User> findAll();
    void update(User user);
    void deleteById(long id);
}
