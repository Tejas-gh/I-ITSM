package com.iitsm.application.service;

import com.iitsm.application.command.SaveSlaPolicyCommand;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.ServiceNotFoundException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.SlaPolicy;
import com.iitsm.domain.sla.SlaPolicyResolver;
import com.iitsm.infrastructure.persistence.memory.InMemoryItServiceRepository;
import com.iitsm.infrastructure.persistence.memory.InMemorySlaPolicyRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SlaPolicyAdminServiceTest {

    private InMemorySlaPolicyRepository policyRepository;
    private InMemoryItServiceRepository serviceRepository;
    private InMemoryUserRepository userRepository;
    private AdminPermissionPolicy permissionPolicy;
    private SlaPolicyAdminService adminService;
    private SlaPolicyResolver resolver;

    private Administrator admin;
    private Employee employee;
    private ItService paymentService;

    @BeforeEach
    void setUp() {
        policyRepository = new InMemorySlaPolicyRepository();
        serviceRepository = new InMemoryItServiceRepository();
        userRepository = new InMemoryUserRepository();
        permissionPolicy = new AdminPermissionPolicy();
        adminService = new SlaPolicyAdminService(policyRepository, serviceRepository, userRepository, permissionPolicy);
        resolver = new SlaPolicyResolver(policyRepository);

        admin = TestData.admin("admin-1");
        employee = TestData.employee("emp-1");
        paymentService = TestData.service("SRV-PAYMENT", BusinessCriticality.CRITICAL);

        userRepository.save(admin);
        userRepository.save(employee);
        serviceRepository.save(paymentService);
    }

    @Test
    @DisplayName("Admin saves default policy and per-service override; resolver correctly prefers override over default")
    void savePolicies_overrideVersusDefaultWorks() {
        // 1. Save default policy for P1: 5 min response, 30 min resolution
        SaveSlaPolicyCommand defaultCmd = new SaveSlaPolicyCommand(
                admin.getId(), null, PriorityLevel.P1,
                Duration.ofMinutes(5), Duration.ofMinutes(30)
        );
        SlaPolicy defaultPolicy = adminService.savePolicy(defaultCmd);
        assertTrue(defaultPolicy.isDefault());

        // Resolver finds default policy when querying another service without override
        SlaPolicy resolvedDefault = resolver.resolve("SRV-OTHER", PriorityLevel.P1);
        assertEquals(defaultPolicy.responseTarget(), resolvedDefault.responseTarget());
        assertEquals(defaultPolicy.resolutionTarget(), resolvedDefault.resolutionTarget());

        // 2. Save per-service override for SRV-PAYMENT: 2 min response, 15 min resolution
        SaveSlaPolicyCommand overrideCmd = new SaveSlaPolicyCommand(
                admin.getId(), paymentService.getId(), PriorityLevel.P1,
                Duration.ofMinutes(2), Duration.ofMinutes(15)
        );
        SlaPolicy overridePolicy = adminService.savePolicy(overrideCmd);
        assertFalse(overridePolicy.isDefault());

        // Resolver prefers override for SRV-PAYMENT
        SlaPolicy resolvedOverride = resolver.resolve(paymentService.getId(), PriorityLevel.P1);
        assertEquals(Duration.ofMinutes(2), resolvedOverride.responseTarget());
        assertEquals(Duration.ofMinutes(15), resolvedOverride.resolutionTarget());

        // Resolver still falls back to default for SRV-OTHER
        SlaPolicy resolvedFallback = resolver.resolve("SRV-OTHER", PriorityLevel.P1);
        assertEquals(Duration.ofMinutes(5), resolvedFallback.responseTarget());
    }

    @Test
    @DisplayName("Saving override policy for non-existent service throws ServiceNotFoundException")
    void nonExistentService_throwsServiceNotFound() {
        SaveSlaPolicyCommand cmd = new SaveSlaPolicyCommand(
                admin.getId(), "UNKNOWN-SRV", PriorityLevel.P1,
                Duration.ofMinutes(5), Duration.ofMinutes(30)
        );
        assertThrows(ServiceNotFoundException.class, () -> adminService.savePolicy(cmd));
    }

    @Test
    @DisplayName("Response target exceeding resolution target throws InvalidInputException")
    void invalidTargets_throwsInvalidInput() {
        assertThrows(InvalidInputException.class, () ->
                new SaveSlaPolicyCommand(
                        admin.getId(), null, PriorityLevel.P1,
                        Duration.ofMinutes(60), Duration.ofMinutes(30) // response > resolution
                ));
    }

    @Test
    @DisplayName("Non-admin role throws UnauthorizedOperationException")
    void nonAdmin_throwsUnauthorizedOperation() {
        SaveSlaPolicyCommand cmd = new SaveSlaPolicyCommand(
                employee.getId(), null, PriorityLevel.P1,
                Duration.ofMinutes(5), Duration.ofMinutes(30)
        );
        assertThrows(UnauthorizedOperationException.class, () -> adminService.savePolicy(cmd));
    }
}
