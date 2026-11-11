package com.iitsm.application.service;

import com.iitsm.application.command.SaveCategoryCommand;
import com.iitsm.domain.exception.CategoryNotFoundException;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Employee;
import com.iitsm.infrastructure.persistence.memory.InMemoryCategoryRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CategoryAdminServiceTest {

    private InMemoryCategoryRepository categoryRepository;
    private InMemoryUserRepository userRepository;
    private AdminPermissionPolicy permissionPolicy;
    private CategoryAdminService adminService;

    private Administrator admin;
    private Employee employee;

    @BeforeEach
    void setUp() {
        categoryRepository = new InMemoryCategoryRepository();
        userRepository = new InMemoryUserRepository();
        permissionPolicy = new AdminPermissionPolicy();
        adminService = new CategoryAdminService(categoryRepository, userRepository, permissionPolicy);

        admin = TestData.admin("admin-1");
        employee = TestData.employee("emp-1");
        userRepository.save(admin);
        userRepository.save(employee);
    }

    @Test
    @DisplayName("Admin can save category, activate, and deactivate")
    void saveAndToggleCategory() {
        SaveCategoryCommand cmd = new SaveCategoryCommand(admin.getId(), "CYBER_SECURITY", "Cyber Security", true);
        Category created = adminService.saveCategory(cmd);

        assertNotNull(created);
        assertEquals("CYBER_SECURITY", created.getId());
        assertEquals("Cyber Security", created.getName());
        assertTrue(created.isActive());

        // Deactivate category
        Category deactivated = adminService.deactivateCategory(admin.getId(), "CYBER_SECURITY");
        assertFalse(deactivated.isActive());

        List<Category> allActive = adminService.findAllActive();
        assertTrue(allActive.isEmpty());

        List<Category> all = adminService.findAll();
        assertEquals(1, all.size());

        // Reactivate category
        Category reactivated = adminService.activateCategory(admin.getId(), "CYBER_SECURITY");
        assertTrue(reactivated.isActive());
        assertEquals(1, adminService.findAllActive().size());
    }

    @Test
    @DisplayName("Category id must strictly adhere to uppercase naming format [A-Z][A-Z0-9_]*")
    void categoryId_rulesEnforced() {
        // Lowercase category id
        SaveCategoryCommand lowercaseCmd = new SaveCategoryCommand(admin.getId(), "network_issue", "Network", true);
        assertThrows(InvalidInputException.class, () -> adminService.saveCategory(lowercaseCmd));

        // Starting with digit or special character
        SaveCategoryCommand digitCmd = new SaveCategoryCommand(admin.getId(), "1NETWORK", "Network", true);
        assertThrows(InvalidInputException.class, () -> adminService.saveCategory(digitCmd));
    }

    @Test
    @DisplayName("Non-admin actor throws UnauthorizedOperationException")
    void nonAdmin_throwsUnauthorizedOperation() {
        SaveCategoryCommand cmd = new SaveCategoryCommand(employee.getId(), "NETWORK", "Network", true);
        assertThrows(UnauthorizedOperationException.class, () -> adminService.saveCategory(cmd));
        assertThrows(UnauthorizedOperationException.class, () -> adminService.activateCategory(employee.getId(), "NETWORK"));
        assertThrows(UnauthorizedOperationException.class, () -> adminService.deactivateCategory(employee.getId(), "NETWORK"));
    }

    @Test
    @DisplayName("Activating non-existent category throws CategoryNotFoundException")
    void activateNonExistent_throwsCategoryNotFound() {
        assertThrows(CategoryNotFoundException.class, () ->
                adminService.activateCategory(admin.getId(), "NON_EXISTENT_CAT"));
    }
}
