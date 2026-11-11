package com.iitsm.application.service;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.exception.UserNotFoundException;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminPermissionPolicyTest {

    private InMemoryUserRepository userRepository;
    private AdminPermissionPolicy policy;

    private Administrator admin;
    private Administrator itManager;
    private Employee employee;
    private Technician technician;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        policy = new AdminPermissionPolicy();

        admin = TestData.admin("admin-1");
        itManager = new Administrator("it-mgr-1", "Manager", "mgr@iitsm.com", Role.IT_MANAGER);
        employee = TestData.employee("emp-1");
        technician = TestData.technician("tech-1", 5);

        userRepository.save(admin);
        userRepository.save(itManager);
        userRepository.save(employee);
        userRepository.save(technician);
    }

    @Test
    @DisplayName("Administrator and IT_MANAGER succeed check")
    void adminRoles_allowed() {
        assertDoesNotThrow(() -> policy.check(admin));
        assertDoesNotThrow(() -> policy.check(itManager));
        assertDoesNotThrow(() -> policy.check(admin.getId(), userRepository));
        assertDoesNotThrow(() -> policy.check(itManager.getId(), userRepository));
    }

    @Test
    @DisplayName("Non-admin roles throw UnauthorizedOperationException")
    void nonAdminRoles_rejected() {
        assertThrows(UnauthorizedOperationException.class, () -> policy.check(employee));
        assertThrows(UnauthorizedOperationException.class, () -> policy.check(technician));
        assertThrows(UnauthorizedOperationException.class, () -> policy.check(employee.getId(), userRepository));
        assertThrows(UnauthorizedOperationException.class, () -> policy.check(technician.getId(), userRepository));
    }

    @Test
    @DisplayName("Inactive administrator throws UnauthorizedOperationException")
    void inactiveAdmin_rejected() {
        admin.deactivate();
        assertThrows(UnauthorizedOperationException.class, () -> policy.check(admin));
        assertThrows(UnauthorizedOperationException.class, () -> policy.check(admin.getId(), userRepository));
    }

    @Test
    @DisplayName("Unknown user throws UserNotFoundException")
    void unknownUser_throwsUserNotFound() {
        assertThrows(UserNotFoundException.class, () -> policy.check("UNKNOWN_USER", userRepository));
    }

    @Test
    @DisplayName("Null or blank arguments throw InvalidInputException")
    void invalidArguments() {
        assertThrows(InvalidInputException.class, () -> policy.check(null));
        assertThrows(InvalidInputException.class, () -> policy.check(null, userRepository));
        assertThrows(InvalidInputException.class, () -> policy.check("   ", userRepository));
        assertThrows(InvalidInputException.class, () -> policy.check("admin-1", null));
    }
}
