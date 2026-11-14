package com.iitsm.api;

import com.iitsm.api.dto.CategoryResponse;
import com.iitsm.api.dto.SaveCategoryRequest;
import com.iitsm.application.command.SaveCategoryCommand;
import com.iitsm.application.service.CategoryAdminService;
import com.iitsm.domain.exception.CategoryNotFoundException;
import com.iitsm.domain.model.Category;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for managing incident categories and their activation status.
 * Creation and mutation are restricted to administrators.
 */
@RestController
@RequestMapping({"/api/admin/categories", "/api/categories"})
@Transactional
public class CategoryAdminController {

    private final CategoryAdminService categoryAdminService;

    public CategoryAdminController(CategoryAdminService categoryAdminService) {
        this.categoryAdminService = categoryAdminService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse saveCategory(@RequestBody SaveCategoryRequest request, Authentication authentication) {
        SaveCategoryCommand command = new SaveCategoryCommand(
                authentication.getName(),
                request.id(),
                request.name(),
                request.active()
        );
        Category saved = categoryAdminService.saveCategory(command);
        return CategoryResponse.from(saved);
    }

    @PostMapping("/{id}/activate")
    public CategoryResponse activateCategory(@PathVariable String id, Authentication authentication) {
        Category activated = categoryAdminService.activateCategory(authentication.getName(), id);
        return CategoryResponse.from(activated);
    }

    @PostMapping("/{id}/deactivate")
    public CategoryResponse deactivateCategory(@PathVariable String id, Authentication authentication) {
        Category deactivated = categoryAdminService.deactivateCategory(authentication.getName(), id);
        return CategoryResponse.from(deactivated);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryAdminService.findAll()
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @GetMapping("/active")
    @Transactional(readOnly = true)
    public List<CategoryResponse> listActiveCategories() {
        return categoryAdminService.findAllActive()
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public CategoryResponse getCategory(@PathVariable String id) {
        return categoryAdminService.findById(id)
                .map(CategoryResponse::from)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + id));
    }
}
