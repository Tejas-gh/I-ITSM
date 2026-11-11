package com.iitsm.application.service;

import com.iitsm.application.command.SaveItServiceCommand;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.ItService;
import com.iitsm.infrastructure.persistence.memory.InMemoryItServiceRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ItServiceAdminServiceTest {

    private InMemoryItServiceRepository serviceRepository;
    private InMemoryUserRepository userRepository;
    private AdminPermissionPolicy permissionPolicy;
    private ItServiceAdminService adminService;

    private Administrator admin;
    private Employee employee;

    @BeforeEach
    void setUp() {
        serviceRepository = new InMemoryItServiceRepository();
        userRepository = new InMemoryUserRepository();
        permissionPolicy = new AdminPermissionPolicy();
        adminService = new ItServiceAdminService(serviceRepository, userRepository, permissionPolicy);

        admin = TestData.admin("admin-1");
        employee = TestData.employee("emp-1");
        userRepository.save(admin);
        userRepository.save(employee);
    }

    @Test
    @DisplayName("Admin can save and update an IT service")
    void saveService_success() {
        SaveItServiceCommand cmd = new SaveItServiceCommand(
                admin.getId(), "SRV-BILLING", "Billing Platform", BusinessCriticality.HIGH
        );
        ItService created = adminService.saveService(cmd);

        assertNotNull(created);
        assertEquals("SRV-BILLING", created.getId());
        assertEquals("Billing Platform", created.getName());
        assertEquals(BusinessCriticality.HIGH, created.getBusinessCriticality());

        Optional<ItService> found = adminService.findById("SRV-BILLING");
        assertTrue(found.isPresent());
        assertEquals("Billing Platform", found.get().getName());

        // Update name and criticality under same id
        SaveItServiceCommand updateCmd = new SaveItServiceCommand(
                admin.getId(), "SRV-BILLING", "Billing Engine V2", BusinessCriticality.CRITICAL
        );
        ItService updated = adminService.saveService(updateCmd);
        assertEquals("Billing Engine V2", updated.getName());
        assertEquals(BusinessCriticality.CRITICAL, updated.getBusinessCriticality());

        List<ItService> all = adminService.findAll();
        assertEquals(1, all.size());
    }

    @Test
    @DisplayName("Non-admin actor throws UnauthorizedOperationException")
    void nonAdmin_throwsUnauthorizedOperation() {
        SaveItServiceCommand cmd = new SaveItServiceCommand(
                employee.getId(), "SRV-TEST", "Test Service", BusinessCriticality.LOW
        );
        assertThrows(UnauthorizedOperationException.class, () -> adminService.saveService(cmd));
    }

    @Test
    @DisplayName("Invalid command arguments throw InvalidInputException")
    void invalidInput() {
        assertThrows(InvalidInputException.class, () ->
                new SaveItServiceCommand(null, "SRV-1", "Name", BusinessCriticality.LOW));
        assertThrows(InvalidInputException.class, () ->
                new SaveItServiceCommand(admin.getId(), "", "Name", BusinessCriticality.LOW));
        assertThrows(InvalidInputException.class, () ->
                new SaveItServiceCommand(admin.getId(), "SRV-1", "   ", BusinessCriticality.LOW));
        assertThrows(InvalidInputException.class, () ->
                new SaveItServiceCommand(admin.getId(), "SRV-1", "Name", null));
        assertThrows(InvalidInputException.class, () -> adminService.saveService(null));
        assertThrows(InvalidInputException.class, () -> adminService.findById(""));
    }
}
