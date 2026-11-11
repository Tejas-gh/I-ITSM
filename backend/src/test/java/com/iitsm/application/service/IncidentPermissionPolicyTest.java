package com.iitsm.application.service;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentDraft;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.model.User;
import com.iitsm.domain.priority.PriorityResult;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class IncidentPermissionPolicyTest {

    private final IncidentPermissionPolicy policy = new IncidentPermissionPolicy();
    private final Instant now = Instant.parse("2026-11-16T09:00:00Z");

    private Employee reporter;
    private Employee otherEmployee;
    private Technician assignee;
    private Technician otherTechnician;
    private Technician teamLead;
    private Administrator admin;
    private Administrator itManager;
    private Incident incident;

    @BeforeEach
    void setUp() {
        reporter = TestData.employee("EMP01");
        otherEmployee = TestData.employee("EMP02");
        assignee = TestData.technician("TECH01", 5);
        otherTechnician = TestData.technician("TECH02", 5);
        teamLead = TestData.teamLead("LEAD01");
        admin = TestData.admin("ADMIN01");
        itManager = new Administrator("ITMGR01", "IT Manager", "itmgr@corp.com", Role.IT_MANAGER);

        IncidentDraft draft = TestData.draft(
                reporter,
                TestData.service("SRV-01", BusinessCriticality.MEDIUM),
                TestData.category("APPLICATION"),
                Impact.HIGH,
                Urgency.HIGH,
                5
        );
        incident = new Incident("INC1001", draft, new PriorityResult(18, PriorityLevel.P2), now);
        incident.classify();
        incident.assign(assignee, now.plusSeconds(30));
    }

    @Test
    @DisplayName("inactive actor is always rejected across all operations")
    void check_inactiveActor_throwsUnauthorizedOperationException() {
        reporter.deactivate();
        assertThrows(UnauthorizedOperationException.class, () -> policy.check(reporter, IncidentOperation.CREATE, null));
        assertThrows(UnauthorizedOperationException.class, () -> policy.check(reporter, IncidentOperation.VIEW, incident));

        admin.deactivate();
        assertThrows(UnauthorizedOperationException.class, () -> policy.check(admin, IncidentOperation.CLOSE, incident));
    }

    @Test
    @DisplayName("CREATE operation allowed only for Employee")
    void check_createOperation_allowedOnlyForEmployee() {
        assertDoesNotThrow(() -> policy.check(reporter, IncidentOperation.CREATE, null));
        assertThrows(UnauthorizedOperationException.class, () -> policy.check(assignee, IncidentOperation.CREATE, null));
        assertThrows(UnauthorizedOperationException.class, () -> policy.check(admin, IncidentOperation.CREATE, null));
    }

    @Test
    @DisplayName("VIEW operation allowed for reporter, assignee, or any admin")
    void check_viewOperation_allowedForReporterAssigneeAndAdmins() {
        // Reporter can view
        assertDoesNotThrow(() -> policy.check(reporter, IncidentOperation.VIEW, incident));
        // Other employee cannot view
        assertThrows(UnauthorizedOperationException.class, () -> policy.check(otherEmployee, IncidentOperation.VIEW, incident));

        // Assigned technician can view
        assertDoesNotThrow(() -> policy.check(assignee, IncidentOperation.VIEW, incident));
        // Other technician cannot view
        assertThrows(UnauthorizedOperationException.class, () -> policy.check(otherTechnician, IncidentOperation.VIEW, incident));

        // Admins can view any incident
        assertDoesNotThrow(() -> policy.check(admin, IncidentOperation.VIEW, incident));
        assertDoesNotThrow(() -> policy.check(itManager, IncidentOperation.VIEW, incident));
    }

    @Test
    @DisplayName("ASSIGN operation allowed only for Administrator and IT_MANAGER")
    void check_assignOperation_allowedOnlyForAdmins() {
        assertDoesNotThrow(() -> policy.check(admin, IncidentOperation.ASSIGN, incident));
        assertDoesNotThrow(() -> policy.check(itManager, IncidentOperation.ASSIGN, incident));

        assertThrows(UnauthorizedOperationException.class, () -> policy.check(reporter, IncidentOperation.ASSIGN, incident));
        assertThrows(UnauthorizedOperationException.class, () -> policy.check(assignee, IncidentOperation.ASSIGN, incident));
        assertThrows(UnauthorizedOperationException.class, () -> policy.check(teamLead, IncidentOperation.ASSIGN, incident));
    }

    @Test
    @DisplayName("Technician workflow operations allowed only for assigned technician")
    void check_technicianOperations_allowedOnlyForAssignedTechnician() {
        IncidentOperation[] techOps = {
                IncidentOperation.ACKNOWLEDGE,
                IncidentOperation.START_WORK,
                IncidentOperation.ADD_INVESTIGATION_NOTE,
                IncidentOperation.REQUEST_INFO,
                IncidentOperation.RESOLVE
        };

        for (IncidentOperation op : techOps) {
            assertDoesNotThrow(() -> policy.check(assignee, op, incident), "Failed for " + op);
            assertThrows(UnauthorizedOperationException.class, () -> policy.check(otherTechnician, op, incident), "Should reject other tech for " + op);
            assertThrows(UnauthorizedOperationException.class, () -> policy.check(reporter, op, incident), "Should reject reporter for " + op);
            assertThrows(UnauthorizedOperationException.class, () -> policy.check(admin, op, incident), "Should reject admin for " + op);
        }
    }

    @Test
    @DisplayName("Employee response workflow operations allowed only for reporting employee")
    void check_employeeOperations_allowedOnlyForReportingEmployee() {
        IncidentOperation[] employeeOps = {
                IncidentOperation.RESPOND_INFO,
                IncidentOperation.ADD_ADDITIONAL_INFO,
                IncidentOperation.VERIFY,
                IncidentOperation.REJECT_RESOLUTION,
                IncidentOperation.SUBMIT_FEEDBACK
        };

        for (IncidentOperation op : employeeOps) {
            assertDoesNotThrow(() -> policy.check(reporter, op, incident), "Failed for " + op);
            assertThrows(UnauthorizedOperationException.class, () -> policy.check(otherEmployee, op, incident), "Should reject other employee for " + op);
            assertThrows(UnauthorizedOperationException.class, () -> policy.check(assignee, op, incident), "Should reject assignee for " + op);
            assertThrows(UnauthorizedOperationException.class, () -> policy.check(admin, op, incident), "Should reject admin for " + op);
        }
    }

    @Test
    @DisplayName("CLOSE operation allowed only for Administrator and IT_MANAGER")
    void check_closeOperation_allowedOnlyForAdmins() {
        assertDoesNotThrow(() -> policy.check(admin, IncidentOperation.CLOSE, incident));
        assertDoesNotThrow(() -> policy.check(itManager, IncidentOperation.CLOSE, incident));

        assertThrows(UnauthorizedOperationException.class, () -> policy.check(reporter, IncidentOperation.CLOSE, incident));
        assertThrows(UnauthorizedOperationException.class, () -> policy.check(assignee, IncidentOperation.CLOSE, incident));
    }

    @Test
    @DisplayName("UPDATE_PRIORITY allowed for admin or assigned technician")
    void check_updatePriorityOperation_allowedForAdminAndAssignedTech() {
        assertDoesNotThrow(() -> policy.check(admin, IncidentOperation.UPDATE_PRIORITY, incident));
        assertDoesNotThrow(() -> policy.check(itManager, IncidentOperation.UPDATE_PRIORITY, incident));
        assertDoesNotThrow(() -> policy.check(assignee, IncidentOperation.UPDATE_PRIORITY, incident));

        assertThrows(UnauthorizedOperationException.class, () -> policy.check(otherTechnician, IncidentOperation.UPDATE_PRIORITY, incident));
        assertThrows(UnauthorizedOperationException.class, () -> policy.check(reporter, IncidentOperation.UPDATE_PRIORITY, incident));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class)
    @DisplayName("SUGGEST_CATEGORY operation allowed for all active roles")
    void check_suggestCategory_allowedForAllActiveRoles(Role role) {
        User user = switch (role) {
            case EMPLOYEE -> reporter;
            case TECHNICIAN -> assignee;
            case TEAM_LEAD -> teamLead;
            case ADMINISTRATOR -> admin;
            case IT_MANAGER -> itManager;
        };
        assertDoesNotThrow(() -> policy.check(user, IncidentOperation.SUGGEST_CATEGORY, null));
    }

    @Test
    @DisplayName("check validates null arguments")
    void check_nullArguments_throwsInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> policy.check(null, IncidentOperation.CREATE, null));
        assertThrows(InvalidInputException.class, () -> policy.check(reporter, null, null));
        assertThrows(InvalidInputException.class, () -> policy.check(reporter, IncidentOperation.VIEW, null));
    }
}
