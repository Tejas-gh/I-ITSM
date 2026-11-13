package com.iitsm.api;

import com.iitsm.api.dto.AuditEntryResponse;
import com.iitsm.application.service.AdminPermissionPolicy;
import com.iitsm.application.service.AuditQueryService;
import com.iitsm.domain.audit.AuditEntry;
import com.iitsm.domain.audit.AuditRepository;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryAuditRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditControllerTest {

    private AuditRepository auditRepository;
    private UserRepository userRepository;
    private AuditController controller;

    private static Authentication authFor(String userId) {
        return new TestingAuthenticationToken(userId, null);
    }

    private final Authentication adminAuth = authFor("ADMIN01");
    private final Authentication employeeAuth = authFor("EMP01");

    @BeforeEach
    void setUp() {
        auditRepository = new InMemoryAuditRepository();
        userRepository = new InMemoryUserRepository();
        AdminPermissionPolicy policy = new AdminPermissionPolicy();
        AuditQueryService queryService = new AuditQueryService(auditRepository, userRepository, policy);
        controller = new AuditController(queryService);

        Administrator admin = TestData.admin("ADMIN01");
        Employee employee = TestData.employee("EMP01");
        userRepository.save(admin);
        userRepository.save(employee);
    }

    @Test
    void getAuditByIncidentId_asAdmin_returnsEntries() {
        AuditEntry entry = new AuditEntry(
                Instant.parse("2026-11-16T10:00:00Z"),
                "EMP01",
                "INC1001",
                IncidentStatus.NEW,
                IncidentStatus.CLASSIFIED,
                "CREATE",
                "Incident created"
        );
        auditRepository.save(entry);

        List<AuditEntryResponse> response = controller.getAuditByIncidentId("INC1001", adminAuth);

        assertEquals(1, response.size());
        assertEquals("INC1001", response.get(0).incidentId());
        assertEquals("CREATE", response.get(0).action());
    }

    @Test
    void getAuditByIncidentId_asEmployee_throwsUnauthorized() {
        assertThrows(UnauthorizedOperationException.class, () ->
                controller.getAuditByIncidentId("INC1001", employeeAuth));
    }

    @Test
    void getAllAudit_asAdmin_returnsAllEntries() {
        auditRepository.save(new AuditEntry(
                Instant.parse("2026-11-16T10:00:00Z"),
                "EMP01", "INC1001", IncidentStatus.NEW, IncidentStatus.CLASSIFIED, "CREATE", "First"
        ));
        auditRepository.save(new AuditEntry(
                Instant.parse("2026-11-16T10:05:00Z"),
                "TECH01", "INC1001", IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED, "ASSIGN", "Second"
        ));

        List<AuditEntryResponse> response = controller.getAllAudit(adminAuth);

        assertEquals(2, response.size());
    }

    @Test
    void getAllAudit_asEmployee_throwsUnauthorized() {
        assertThrows(UnauthorizedOperationException.class, () ->
                controller.getAllAudit(employeeAuth));
    }

    @Test
    void getAuditByIncidentId_blankId_throwsInvalidInput() {
        assertThrows(InvalidInputException.class, () ->
                controller.getAuditByIncidentId("  ", adminAuth));
    }
}
