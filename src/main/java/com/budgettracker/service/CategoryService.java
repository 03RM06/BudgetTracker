package com.budgettracker.service;

import com.budgettracker.domain.BudgetTrackerException;
import com.budgettracker.domain.Category;
import com.budgettracker.domain.CategoryType;
import com.budgettracker.domain.DuplicateResourceException;
import com.budgettracker.persistence.CategoryRepository;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

public class CategoryService {

    private final CategoryRepository categoryRepo;

    public CategoryService(CategoryRepository categoryRepo) {
        this.categoryRepo = categoryRepo;
    }

    public Category createCategory(long userId, String name, CategoryType type, Long parentId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
        try {
            boolean nameTaken = categoryRepo.findByUserIdAndType(userId, type).stream()
                    .anyMatch(c -> c.getName().equalsIgnoreCase(name));
            if (nameTaken) {
                throw new DuplicateResourceException(
                        "A category with that name already exists for this user and type: " + name);
            }
            Category category = new Category();
            category.setUserId(userId);
            category.setName(name);
            category.setType(type);
            category.setParentId(parentId);
            Instant now = Instant.now();
            category.setCreatedAt(now);
            category.setUpdatedAt(now);
            return categoryRepo.save(category);
        } catch (DuplicateResourceException e) {
            throw e;
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to create category", e);
        }
    }

    public List<Category> getCategoriesByType(long userId, CategoryType type) {
        try {
            return categoryRepo.findByUserIdAndType(userId, type);
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to fetch categories", e);
        }
    }

    public List<Category> getAllCategories(long userId) {
        try {
            return categoryRepo.findByUserId(userId);
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to fetch categories", e);
        }
    }

    public void deleteCategory(long categoryId) {
        try {
            categoryRepo.deleteById(categoryId);
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to delete category", e);
        }
    }
}
