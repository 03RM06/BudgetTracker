package com.budgettracker.persistence;

import com.budgettracker.domain.User;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    User save(User user) throws SQLException;
    Optional<User> findById(long id) throws SQLException;
    Optional<User> findByEmail(String email) throws SQLException;
    List<User> findAll() throws SQLException;
    void update(User user) throws SQLException;
    void deleteById(long id) throws SQLException;
}
