package com.budgettracker.service;

import com.budgettracker.domain.BudgetTrackerException;
import com.budgettracker.domain.Category;
import com.budgettracker.domain.CategoryType;
import com.budgettracker.domain.DuplicateResourceException;
import com.budgettracker.persistence.CategoryRepository;
import com.budgettracker.persistence.DataAccessException;
import com.budgettracker.persistence.TxRunner;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final TxRunner txRunner;
    private final CategoryRepository categoryRepo;

    public CategoryService(TxRunner txRunner, CategoryRepository categoryRepo) {
        this.txRunner = txRunner;
        this.categoryRepo = categoryRepo;
    }

    public Category createCategory(long userId, String name, CategoryType type, Long parentId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
        return txRunner.inTransaction(() -> {
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
            } catch (DataAccessException e) {
                log.error("Failed to create category name={}", name, e);
                throw new BudgetTrackerException("Failed to create category", e);
            }
        });
    }

    public List<Category> getCategoriesByType(long userId, CategoryType type) {
        return txRunner.inTransaction(() -> {
            try {
                return categoryRepo.findByUserIdAndType(userId, type);
            } catch (DataAccessException e) {
                log.error("Failed to fetch categories for userId={} type={}", userId, type, e);
                throw new BudgetTrackerException("Failed to fetch categories", e);
            }
        });
    }

    public List<Category> getAllCategories(long userId) {
        return txRunner.inTransaction(() -> {
            try {
                return categoryRepo.findByUserId(userId);
            } catch (DataAccessException e) {
                log.error("Failed to fetch categories for userId={}", userId, e);
                throw new BudgetTrackerException("Failed to fetch categories", e);
            }
        });
    }

    public void deleteCategory(long categoryId) {
        txRunner.inTransaction(() -> {
            try {
                categoryRepo.deleteById(categoryId);
            } catch (DataAccessException e) {
                log.error("Failed to delete category id={}", categoryId, e);
                throw new BudgetTrackerException("Failed to delete category", e);
            }
        });
    }
}
