package com.budgettracker.persistence;

import com.budgettracker.domain.Category;
import com.budgettracker.domain.CategoryType;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    Category save(Category category) throws SQLException;
    Optional<Category> findById(long id) throws SQLException;
    List<Category> findByUserId(long userId) throws SQLException;
    List<Category> findByUserIdAndType(long userId, CategoryType type) throws SQLException;
    void update(Category category) throws SQLException;
    void deleteById(long id) throws SQLException;
}
