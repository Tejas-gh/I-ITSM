package com.iitsm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.iitsm.api.dto.CreateUserRequest;
import com.iitsm.api.dto.UpdateUserRequest;
import com.iitsm.api.dto.UserResponse;
import com.iitsm.application.security.AccessToken;
import com.iitsm.application.service.AdminPermissionPolicy;
import com.iitsm.application.service.AuthService;
import com.iitsm.application.service.UserAdminService;
import com.iitsm.domain.exception.CategoryNotFoundException;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.exception.UserNotFoundException;
import com.iitsm.domain.factory.UserFactory;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Role;
import com.iitsm.infrastructure.persistence.memory.InMemoryCategoryRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.FakeCredentialsRepository;
import com.iitsm.support.FakePasswordHasher;
import com.iitsm.support.TestData;

class UserAdminControllerTest {

    private static final String PASSWORD = "Initial-Pass-123";

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final FakeCredentialsRepository credentials = new FakeCredentialsRepository();
    private final UserAdminController controller;

    UserAdminControllerTest() {
        InMemoryCategoryRepository categories = new InMemoryCategoryRepository();
        for (Category category : TestData.defaultCategories()) {
            categories.save(category);
        }
        UserFactory factory = new UserFactory();
        AuthService authService = new AuthService(users, credentials, new FakePasswordHasher(),
                user -> new AccessToken("t", Instant.parse("2030-01-01T00:00:00Z")), factory);
        controller = new UserAdminController(
                new UserAdminService(users, categories, new AdminPermissionPolicy(), factory, authService));
        users.save(TestData.admin("ADMIN01"));
        users.save(TestData.employee("EMP01"));
        users.save(TestData.technician("TECH01", 3));
    }

    private static Authentication as(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, "n/a");
    }

    private static CreateUserRequest employeeRequest(String id) {
        return new CreateUserRequest(id, "Emp " + id, id.toLowerCase() + "@corp.com", Role.EMPLOYEE, "Finance",
                null, null, null, PASSWORD);
    }

    @Test
    void create_employee_returnsTheUserWithoutAnyPassword() {
        UserResponse response = controller.create(employeeRequest("E1"), as("ADMIN01"));

        assertEquals("E1", response.id());
        assertEquals(Role.EMPLOYEE, response.role());
        assertEquals("Finance", response.department());
        assertTrue(response.active());
        assertNull(response.skillLevels());
        assertFalse(response.toString().contains(PASSWORD));
        assertFalse(response.toString().contains(new FakePasswordHasher().hash(PASSWORD)));
    }

    @Test
    void userResponse_hasNoPasswordOrHashComponent() {
        List<String> names = Arrays.stream(UserResponse.class.getRecordComponents())
                .map(RecordComponent::getName).map(String::toLowerCase).toList();

        assertTrue(names.stream().noneMatch(name -> name.contains("password") || name.contains("hash")));
    }

    @Test
    void create_everyRole_returnsTheMatchingRoleAndRoleSpecificFields() {
        UserResponse technician = controller.create(new CreateUserRequest("T1", "Tech", "t1@corp.com",
                Role.TECHNICIAN, null, Map.of("NETWORK", 3), 4, 3, PASSWORD), as("ADMIN01"));
        UserResponse lead = controller.create(new CreateUserRequest("L1", "Lead", "l1@corp.com",
                Role.TEAM_LEAD, null, Map.of(), 8, 5, PASSWORD), as("ADMIN01"));
        UserResponse admin = controller.create(new CreateUserRequest("A2", "Admin", "a2@corp.com",
                Role.ADMINISTRATOR, null, null, null, null, PASSWORD), as("ADMIN01"));
        UserResponse manager = controller.create(new CreateUserRequest("M1", "Mgr", "m1@corp.com",
                Role.IT_MANAGER, null, null, null, null, PASSWORD), as("ADMIN01"));

        assertEquals(Role.TECHNICIAN, technician.role());
        assertEquals(Map.of("NETWORK", 3), technician.skillLevels());
        assertEquals(Integer.valueOf(4), technician.yearsOfExperience());
        assertEquals(Integer.valueOf(3), technician.maxActiveIncidents());
        assertEquals(Boolean.TRUE, technician.available());
        assertEquals(Role.TEAM_LEAD, lead.role());
        assertEquals(Role.ADMINISTRATOR, admin.role());
        assertEquals(Role.IT_MANAGER, manager.role());
        assertNull(admin.department());
        assertNull(admin.skillLevels());
        assertEquals(4, credentials.size()); // only the 4 created accounts have credentials
    }

    @Test
    void create_duplicateInvalidOrUnknownCategory_areRejected() {
        controller.create(employeeRequest("E2"), as("ADMIN01"));

        assertThrows(InvalidInputException.class, () -> controller.create(employeeRequest("E2"), as("ADMIN01")));
        assertThrows(InvalidInputException.class, () -> controller.create(new CreateUserRequest(
                "E3", "Emp", "bad-email", Role.EMPLOYEE, "IT", null, null, null, PASSWORD), as("ADMIN01")));
        assertThrows(InvalidInputException.class, () -> controller.create(new CreateUserRequest(
                "E4", "Emp", "e4@corp.com", Role.EMPLOYEE, "IT", null, null, null, "short"), as("ADMIN01")));
        assertThrows(InvalidInputException.class, () -> controller.create(new CreateUserRequest(
                "E5", "Emp", "e5@corp.com", null, "IT", null, null, null, PASSWORD), as("ADMIN01")));
        assertThrows(InvalidInputException.class, () -> controller.create(new CreateUserRequest(
                "T2", "Tech", "t2@corp.com", Role.TECHNICIAN, null, Map.of(), null, null, PASSWORD), as("ADMIN01")));
        assertThrows(CategoryNotFoundException.class, () -> controller.create(new CreateUserRequest(
                "T3", "Tech", "t3@corp.com", Role.TECHNICIAN, null, Map.of("NOPE", 2), 1, 2, PASSWORD), as("ADMIN01")));
        assertTrue(users.findById("E3").isEmpty());
        assertTrue(users.findById("T3").isEmpty());
    }

    @Test
    void everyEndpoint_nonAdministrator_isRejectedWithAnUnauthorizedOperationThatMapsTo403() {
        for (String actor : List.of("EMP01", "TECH01")) {
            UnauthorizedOperationException ex = assertThrows(UnauthorizedOperationException.class,
                    () -> controller.create(employeeRequest("E9"), as(actor)));
            assertEquals(403, ApiErrorStatus.httpStatusFor(ex.getCode()));
            assertThrows(UnauthorizedOperationException.class, () -> controller.list(as(actor)));
            assertThrows(UnauthorizedOperationException.class, () -> controller.get("EMP01", as(actor)));
            assertThrows(UnauthorizedOperationException.class, () -> controller.update("EMP01",
                    new UpdateUserRequest("X", null, null, null, null, null, null), as(actor)));
        }
        assertTrue(users.findById("E9").isEmpty());
        assertEquals("Finance", ((com.iitsm.domain.model.Employee) users.findById("EMP01").orElseThrow())
                .getDepartment());
    }

    @Test
    void list_and_get_returnUsersWithoutSecrets() {
        controller.create(employeeRequest("E6"), as("ADMIN01"));

        List<UserResponse> all = controller.list(as("ADMIN01"));

        assertEquals(4, all.size());
        assertEquals("E6", controller.get("E6", as("ADMIN01")).id());
        assertThrows(UserNotFoundException.class, () -> controller.get("NO-SUCH", as("ADMIN01")));
    }

    @Test
    void update_changesFieldsAndReplacesTechnicianSkills() {
        controller.create(new CreateUserRequest("T4", "Tech", "t4@corp.com", Role.TECHNICIAN, null,
                Map.of("NETWORK", 3, "DATABASE", 2), 4, 3, PASSWORD), as("ADMIN01"));

        UserResponse renamed = controller.update("T4",
                new UpdateUserRequest("Renamed", null, null, null, null, null, null), as("ADMIN01"));
        UserResponse reskilled = controller.update("T4",
                new UpdateUserRequest(null, null, null, Map.of("CLOUD", 5), null, 6, null), as("ADMIN01"));

        assertEquals("Renamed", renamed.name());
        assertEquals(Map.of("NETWORK", 3, "DATABASE", 2), renamed.skillLevels());
        assertEquals(Map.of("CLOUD", 5), reskilled.skillLevels());
        assertEquals(Integer.valueOf(6), reskilled.maxActiveIncidents());
        assertEquals("Renamed", reskilled.name());
        assertEquals(Role.TECHNICIAN, reskilled.role());
    }

    @Test
    void update_canDeactivateAUser_andRejectsInvalidInput() {
        controller.create(employeeRequest("E7"), as("ADMIN01"));

        UserResponse deactivated = controller.update("E7",
                new UpdateUserRequest(null, null, null, null, null, null, false), as("ADMIN01"));

        assertFalse(deactivated.active());
        assertThrows(InvalidInputException.class, () -> controller.update("E7",
                new UpdateUserRequest("  ", null, null, null, null, null, null), as("ADMIN01")));
        assertThrows(UserNotFoundException.class, () -> controller.update("NO-SUCH",
                new UpdateUserRequest("x", null, null, null, null, null, null), as("ADMIN01")));
    }
}
