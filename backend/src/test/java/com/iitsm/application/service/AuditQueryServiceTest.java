package com.iitsm.application.service;

import com.iitsm.domain.audit.AuditEntry;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.infrastructure.persistence.memory.InMemoryAuditRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditQueryServiceTest {

    private InMemoryAuditRepository auditRepository;
    private InMemoryUserRepository userRepository;
    private AdminPermissionPolicy permissionPolicy;
    private AuditQueryService auditQueryService;

    private Administrator admin;
    private Employee employee;

    @BeforeEach
    void setUp() {
        auditRepository = new InMemoryAuditRepository();
        userRepository = new InMemoryUserRepository();
        permissionPolicy = new AdminPermissionPolicy();
        auditQueryService = new AuditQueryService(auditRepository, userRepository, permissionPolicy);

        admin = TestData.admin("admin-1");
        employee = TestData.employee("emp-1");
        userRepository.save(admin);
        userRepository.save(employee);

        auditRepository.save(new AuditEntry(
                Instant.now(), "reporter-1", "INC-001", null, IncidentStatus.NEW, "CREATE", "Created ticket"
        ));
        auditRepository.save(new AuditEntry(
                Instant.now(), "tech-1", "INC-001", IncidentStatus.NEW, IncidentStatus.ASSIGNED, "ASSIGN", "Assigned"
        ));
        auditRepository.save(new AuditEntry(
                Instant.now(), "reporter-2", "INC-002", null, IncidentStatus.NEW, "CREATE", "Created ticket 2"
        ));
    }

    @Test
    @DisplayName("Admin can query audit entries by incident ID and query all entries")
    void admin_queriesAuditLogs() {
        List<AuditEntry> inc1Entries = auditQueryService.findByIncidentId(admin.getId(), "INC-001");
        assertEquals(2, inc1Entries.size());
        assertTrue(inc1Entries.stream().allMatch(e -> e.incidentId().equals("INC-001")));

        List<AuditEntry> allEntries = auditQueryService.findAll(admin.getId());
        assertEquals(3, allEntries.size());
    }

    @Test
    @DisplayName("Non-admin role throws UnauthorizedOperationException (audit queries are admin-only)")
    void nonAdmin_accessDenied() {
        assertThrows(UnauthorizedOperationException.class, () ->
                auditQueryService.findByIncidentId(employee.getId(), "INC-001"));

        assertThrows(UnauthorizedOperationException.class, () ->
                auditQueryService.findAll(employee.getId()));
    }

    @Test
    @DisplayName("Blank incident ID throws InvalidInputException")
    void blankIncidentId_throwsInvalidInput() {
        assertThrows(InvalidInputException.class, () ->
                auditQueryService.findByIncidentId(admin.getId(), "   "));
    }
}
