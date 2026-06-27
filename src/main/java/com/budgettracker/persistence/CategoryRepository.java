package com.budgettracker.persistence;

import com.budgettracker.domain.Category;
import com.budgettracker.domain.CategoryType;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    Category save(Category category);
    Optional<Category> findById(long id);
    List<Category> findByUserId(long userId);
    List<Category> findByUserIdAndType(long userId, CategoryType type);
    void update(Category category);
    void deleteById(long id);
}
