package com.iitsm.application.service;

import com.iitsm.application.command.SaveCategoryCommand;
import com.iitsm.domain.exception.CategoryNotFoundException;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.repository.CategoryRepository;
import com.iitsm.domain.repository.UserRepository;

import java.util.List;
import java.util.Optional;

/**
 * Administrative application service for managing incident categories and their active status.
 */
public final class CategoryAdminService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AdminPermissionPolicy permissionPolicy;

    public CategoryAdminService(CategoryRepository categoryRepository,
                                UserRepository userRepository,
                                AdminPermissionPolicy permissionPolicy) {
        if (categoryRepository == null) {
            throw new InvalidInputException("categoryRepository must not be null");
        }
        if (userRepository == null) {
            throw new InvalidInputException("userRepository must not be null");
        }
        if (permissionPolicy == null) {
            throw new InvalidInputException("permissionPolicy must not be null");
        }
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.permissionPolicy = permissionPolicy;
    }

    /**
     * Creates or updates a category. Restricted to administrators.
     */
    public Category saveCategory(SaveCategoryCommand command) {
        if (command == null) {
            throw new InvalidInputException("command must not be null");
        }
        permissionPolicy.check(command.actorId(), userRepository);

        Category category = new Category(command.id(), command.name());
        if (command.active() != null) {
            if (command.active()) {
                category.activate();
            } else {
                category.deactivate();
            }
        }
        return categoryRepository.save(category);
    }

    /**
     * Activates a category. Restricted to administrators.
     */
    public Category activateCategory(String actorId, String categoryId) {
        permissionPolicy.check(actorId, userRepository);
        if (categoryId == null || categoryId.isBlank()) {
            throw new InvalidInputException("categoryId must not be blank");
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + categoryId));
        category.activate();
        return categoryRepository.save(category);
    }

    /**
     * Deactivates a category. Restricted to administrators.
     */
    public Category deactivateCategory(String actorId, String categoryId) {
        permissionPolicy.check(actorId, userRepository);
        if (categoryId == null || categoryId.isBlank()) {
            throw new InvalidInputException("categoryId must not be blank");
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + categoryId));
        category.deactivate();
        return categoryRepository.save(category);
    }

    public Optional<Category> findById(String id) {
        if (id == null || id.isBlank()) {
            throw new InvalidInputException("id must not be blank");
        }
        return categoryRepository.findById(id);
    }

    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    public List<Category> findAllActive() {
        return categoryRepository.findAllActive();
    }
}
