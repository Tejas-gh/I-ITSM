package com.iitsm.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.iitsm.application.command.CreateUserCommand;
import com.iitsm.application.command.LoginCommand;
import com.iitsm.application.command.SetTechnicianAvailabilityCommand;
import com.iitsm.application.command.UpdateUserCommand;
import com.iitsm.application.security.AccessToken;
import com.iitsm.domain.exception.CategoryNotFoundException;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.InvalidLoginException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.exception.UserNotFoundException;
import com.iitsm.domain.factory.UserFactory;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import com.iitsm.infrastructure.persistence.memory.InMemoryCategoryRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.FakeCredentialsRepository;
import com.iitsm.support.FakePasswordHasher;
import com.iitsm.support.TestData;

class UserAdminServiceTest {

    private static final String ADMIN = "ADMIN01";
    private static final String PASSWORD = "Initial-Pass-123";

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryCategoryRepository categories = new InMemoryCategoryRepository();
    private final FakeCredentialsRepository credentials = new FakeCredentialsRepository();
    private final FakePasswordHasher hasher = new FakePasswordHasher();
    private final UserFactory userFactory = new UserFactory();
    private final AuthService authService = new AuthService(users, credentials, hasher,
            user -> new AccessToken("t", Instant.parse("2030-01-01T00:00:00Z")), userFactory);
    private final UserAdminService service =
            new UserAdminService(users, categories, new AdminPermissionPolicy(), userFactory, authService);

    UserAdminServiceTest() {
        users.save(TestData.admin(ADMIN));
        for (Category category : TestData.defaultCategories()) {
            categories.save(category);
        }
    }

    private CreateUserCommand employee(String id) {
        return new CreateUserCommand(ADMIN, id, "Emp " + id, id.toLowerCase() + "@corp.com", Role.EMPLOYEE,
                "Finance", null, 0, 0, PASSWORD);
    }

    private CreateUserCommand technician(String id, Role role, Map<String, Integer> skills) {
        return new CreateUserCommand(ADMIN, id, "Tech " + id, id.toLowerCase() + "@corp.com", role,
                null, skills, 4, 3, PASSWORD);
    }

    // ---------- role -> subclass ----------

    @Test
    void createUser_employee_createsAnEmployee() {
        User user = service.createUser(employee("E1"));

        Employee employee = assertInstanceOf(Employee.class, user);
        assertEquals(Role.EMPLOYEE, employee.getRole());
        assertEquals("Finance", employee.getDepartment());
        assertTrue(employee.isActive());
    }

    @Test
    void createUser_technicianAndTeamLead_createTechniciansWithTheRequestedRole() {
        Technician technician = assertInstanceOf(Technician.class,
                service.createUser(technician("T1", Role.TECHNICIAN, Map.of("NETWORK", 3))));
        Technician lead = assertInstanceOf(Technician.class,
                service.createUser(technician("L1", Role.TEAM_LEAD, Map.of("DATABASE", 5))));

        assertEquals(Role.TECHNICIAN, technician.getRole());
        assertEquals(Role.TEAM_LEAD, lead.getRole());
        assertEquals(3, technician.skillLevelFor("NETWORK"));
        assertEquals(4, technician.getYearsOfExperience());
        assertEquals(3, technician.getMaxActiveIncidents());
        assertTrue(technician.isAvailable());
    }

    @Test
    void createUser_administratorAndItManager_createAdministratorsWithTheRequestedRole() {
        Administrator admin = assertInstanceOf(Administrator.class, service.createUser(new CreateUserCommand(
                ADMIN, "A2", "Admin Two", "a2@corp.com", Role.ADMINISTRATOR, null, null, 0, 0, PASSWORD)));
        Administrator manager = assertInstanceOf(Administrator.class, service.createUser(new CreateUserCommand(
                ADMIN, "M1", "Manager", "m1@corp.com", Role.IT_MANAGER, null, null, 0, 0, PASSWORD)));

        assertEquals(Role.ADMINISTRATOR, admin.getRole());
        assertEquals(Role.IT_MANAGER, manager.getRole());
    }

    @Test
    void createUser_technicianWithoutSkills_startsWithAnEmptySkillMap() {
        Technician technician = assertInstanceOf(Technician.class,
                service.createUser(technician("T2", Role.TECHNICIAN, null)));

        assertTrue(technician.getSkillLevels().isEmpty());
    }

    // ---------- credentials ----------

    @Test
    void createUser_storesOnlyAHashAndTheUserCanThenLogIn() {
        service.createUser(employee("E2"));

        String stored = credentials.findByUserId("E2").orElseThrow().passwordHash();

        assertNotEquals(PASSWORD, stored);
        assertEquals(hasher.hash(PASSWORD), stored);
        assertEquals("E2", authService.authenticate(new LoginCommand("E2", PASSWORD)).getId());
        assertThrows(InvalidLoginException.class,
                () -> authService.authenticate(new LoginCommand("E2", "Wrong-Password-1")));
    }

    @Test
    void createUser_commandToString_doesNotContainThePassword() {
        assertFalse(employee("E3").toString().contains(PASSWORD));
    }

    // ---------- duplicate / invalid input ----------

    @Test
    void createUser_duplicateId_isRejectedAndKeepsTheOriginal() {
        service.createUser(employee("E4"));

        assertThrows(InvalidInputException.class, () -> service.createUser(new CreateUserCommand(
                ADMIN, "E4", "Impostor", "imp@corp.com", Role.EMPLOYEE, "IT", null, 0, 0, "Another-Pass-9")));

        assertEquals("Emp E4", users.findById("E4").orElseThrow().getName());
        assertEquals(hasher.hash(PASSWORD), credentials.findByUserId("E4").orElseThrow().passwordHash());
    }

    @Test
    void createUser_invalidData_isRejectedAndSavesNothing() {
        int before = users.findAll().size();

        assertThrows(InvalidInputException.class, () -> service.createUser(new CreateUserCommand(
                ADMIN, "E5", "Emp", "not-an-email", Role.EMPLOYEE, "IT", null, 0, 0, PASSWORD)));
        assertThrows(InvalidInputException.class, () -> service.createUser(new CreateUserCommand(
                ADMIN, "E6", "Emp", "e6@corp.com", Role.EMPLOYEE, null, null, 0, 0, PASSWORD)));
        assertThrows(InvalidInputException.class, () -> service.createUser(new CreateUserCommand(
                ADMIN, "E7", "Emp", "e7@corp.com", Role.EMPLOYEE, "IT", null, 0, 0, "short")));
        assertThrows(InvalidInputException.class, () -> service.createUser(new CreateUserCommand(
                ADMIN, "T3", "Tech", "t3@corp.com", Role.TECHNICIAN, null, Map.of(), 1, 0, PASSWORD)));
        assertThrows(InvalidInputException.class, () -> service.createUser(new CreateUserCommand(
                ADMIN, "T4", "Tech", "t4@corp.com", Role.TECHNICIAN, null, Map.of("NETWORK", 9), 1, 2, PASSWORD)));
        assertThrows(InvalidInputException.class, () -> new CreateUserCommand(ADMIN, " ", "x", "x@y.z",
                Role.EMPLOYEE, "IT", null, 0, 0, PASSWORD));
        assertThrows(InvalidInputException.class, () -> new CreateUserCommand(ADMIN, "E8", "x", "x@y.z",
                null, "IT", null, 0, 0, PASSWORD));

        assertEquals(before, users.findAll().size());
        assertEquals(0, credentials.size());
    }

    @Test
    void createUser_technicianSkillForUnknownCategory_isRejectedAndSavesNothing() {
        assertThrows(CategoryNotFoundException.class, () ->
                service.createUser(technician("T5", Role.TECHNICIAN, Map.of("NO_SUCH_CATEGORY", 3))));

        assertTrue(users.findById("T5").isEmpty());
        assertEquals(0, credentials.size());
    }

    // ---------- permissions ----------

    @Test
    void everyOperation_nonAdministrator_isRejectedAndChangesNothing() {
        users.save(TestData.employee("EMP9"));
        users.save(TestData.technician("TECH9", 3));
        users.save(TestData.teamLead("LEAD9"));
        service.createUser(employee("E9"));

        for (String actor : List.of("EMP9", "TECH9", "LEAD9")) {
            assertThrows(UnauthorizedOperationException.class, () -> service.createUser(new CreateUserCommand(
                    actor, "NEW1", "New", "new1@corp.com", Role.EMPLOYEE, "IT", null, 0, 0, PASSWORD)));
            assertThrows(UnauthorizedOperationException.class, () -> service.updateUser(
                    new UpdateUserCommand(actor, "E9", "Hacked", null, null, null, null, null, null)));
            assertThrows(UnauthorizedOperationException.class, () -> service.getUser(actor, "E9"));
            assertThrows(UnauthorizedOperationException.class, () -> service.listUsers(actor));
            assertThrows(UnauthorizedOperationException.class, () -> service.listTechnicians(actor));
            assertThrows(UnauthorizedOperationException.class, () -> service.setTechnicianAvailability(
                    new SetTechnicianAvailabilityCommand(actor, "TECH9", false)));
        }

        assertTrue(users.findById("NEW1").isEmpty());
        assertEquals("Emp E9", users.findById("E9").orElseThrow().getName());
        assertTrue(((Technician) users.findById("TECH9").orElseThrow()).isAvailable());
    }

    @Test
    void anyAdministratorRole_isAllowed_butAnInactiveOrUnknownActorIsNot() {
        users.save(new Administrator("MGR", "Manager", "mgr@corp.com", Role.IT_MANAGER));
        Administrator inactive = new Administrator("OLD", "Old Admin", "old@corp.com", Role.ADMINISTRATOR);
        inactive.deactivate();
        users.save(inactive);

        assertTrue(service.listUsers("MGR").size() >= 1);
        assertThrows(UnauthorizedOperationException.class, () -> service.listUsers("OLD"));
        assertThrows(UserNotFoundException.class, () -> service.listUsers("NOBODY"));
    }

    // ---------- read ----------

    @Test
    void getAndList_returnTheUsers_andUnknownIdIsNotFound() {
        service.createUser(employee("E10"));
        service.createUser(technician("T10", Role.TECHNICIAN, Map.of()));

        assertEquals("E10", service.getUser(ADMIN, "E10").getId());
        assertEquals(3, service.listUsers(ADMIN).size());
        assertEquals(List.of("T10"), service.listTechnicians(ADMIN).stream().map(Technician::getId).toList());
        assertThrows(UserNotFoundException.class, () -> service.getUser(ADMIN, "NO-SUCH"));
    }

    // ---------- update ----------

    @Test
    void updateUser_changesOnlyTheGivenFields_andKeepsTheRoleAndCredentials() {
        service.createUser(employee("E11"));

        User updated = service.updateUser(new UpdateUserCommand(ADMIN, "E11", "Renamed", null, "Legal",
                null, null, null, null));

        Employee employee = assertInstanceOf(Employee.class, updated);
        assertEquals("Renamed", employee.getName());
        assertEquals("e11@corp.com", employee.getEmail());
        assertEquals("Legal", employee.getDepartment());
        assertEquals(Role.EMPLOYEE, employee.getRole());
        assertEquals(hasher.hash(PASSWORD), credentials.findByUserId("E11").orElseThrow().passwordHash());
        assertEquals("Renamed", users.findById("E11").orElseThrow().getName());
    }

    @Test
    void updateUser_technicianSkills_areReplacedUnderTheSameId() {
        service.createUser(technician("T11", Role.TECHNICIAN, Map.of("NETWORK", 3, "DATABASE", 2)));

        Technician updated = assertInstanceOf(Technician.class, service.updateUser(new UpdateUserCommand(
                ADMIN, "T11", null, null, null, Map.of("CLOUD", 4), null, null, null)));

        assertEquals(Map.of("CLOUD", 4), updated.getSkillLevels());
        assertEquals(0, updated.skillLevelFor("NETWORK"));
        assertEquals(4, updated.getYearsOfExperience());
        assertEquals(3, updated.getMaxActiveIncidents());
        Technician stored = (Technician) users.findById("T11").orElseThrow();
        assertEquals(Map.of("CLOUD", 4), stored.getSkillLevels());
        assertEquals(1, users.findAllTechnicians().size());
    }

    @Test
    void updateUser_withoutSkills_keepsTheExistingSkills() {
        service.createUser(technician("T12", Role.TECHNICIAN, Map.of("NETWORK", 3)));

        Technician updated = assertInstanceOf(Technician.class, service.updateUser(new UpdateUserCommand(
                ADMIN, "T12", null, null, null, null, 9, 7, null)));

        assertEquals(Map.of("NETWORK", 3), updated.getSkillLevels());
        assertEquals(9, updated.getYearsOfExperience());
        assertEquals(7, updated.getMaxActiveIncidents());
    }

    @Test
    void updateUser_replacement_preservesTheTechniciansAvailabilityAndActiveFlag() {
        service.createUser(technician("T13", Role.TECHNICIAN, Map.of("NETWORK", 3)));
        service.setTechnicianAvailability(new SetTechnicianAvailabilityCommand(ADMIN, "T13", false));

        Technician renamed = assertInstanceOf(Technician.class, service.updateUser(new UpdateUserCommand(
                ADMIN, "T13", "Renamed Tech", null, null, null, null, null, null)));

        assertFalse(renamed.isAvailable());
        assertTrue(renamed.isActive());

        Technician deactivated = assertInstanceOf(Technician.class, service.updateUser(new UpdateUserCommand(
                ADMIN, "T13", null, null, null, null, null, null, false)));
        assertFalse(deactivated.isActive());
        assertFalse(deactivated.isAssignable());
        assertFalse(((Technician) users.findById("T13").orElseThrow()).isActive());

        Technician reactivated = assertInstanceOf(Technician.class, service.updateUser(new UpdateUserCommand(
                ADMIN, "T13", null, null, null, null, null, null, true)));
        assertTrue(reactivated.isActive());
    }

    @Test
    void updateUser_deactivatedUser_cannotLogInAnymore() {
        service.createUser(employee("E12"));

        service.updateUser(new UpdateUserCommand(ADMIN, "E12", null, null, null, null, null, null, false));

        assertThrows(InvalidLoginException.class,
                () -> authService.authenticate(new LoginCommand("E12", PASSWORD)));
    }

    @Test
    void updateUser_invalidValues_areRejectedAndLeaveTheUserUnchanged() {
        service.createUser(employee("E13"));
        service.createUser(technician("T14", Role.TECHNICIAN, Map.of("NETWORK", 3)));

        assertThrows(InvalidInputException.class, () -> service.updateUser(
                new UpdateUserCommand(ADMIN, "E13", " ", null, null, null, null, null, null)));
        assertThrows(InvalidInputException.class, () -> service.updateUser(
                new UpdateUserCommand(ADMIN, "E13", null, "bad-email", null, null, null, null, null)));
        assertThrows(InvalidInputException.class, () -> service.updateUser(
                new UpdateUserCommand(ADMIN, "T14", null, null, null, Map.of("NETWORK", 6), null, null, null)));
        assertThrows(InvalidInputException.class, () -> service.updateUser(
                new UpdateUserCommand(ADMIN, "T14", null, null, null, null, null, 0, null)));
        assertThrows(CategoryNotFoundException.class, () -> service.updateUser(
                new UpdateUserCommand(ADMIN, "T14", null, null, null, Map.of("NO_SUCH", 2), null, null, null)));

        assertEquals("Emp E13", users.findById("E13").orElseThrow().getName());
        assertEquals(Map.of("NETWORK", 3), ((Technician) users.findById("T14").orElseThrow()).getSkillLevels());
    }

    @Test
    void updateUser_fieldsThatDoNotApplyToTheRole_areRejected() {
        service.createUser(employee("E14"));
        service.createUser(technician("T15", Role.TECHNICIAN, Map.of()));

        assertThrows(InvalidInputException.class, () -> service.updateUser(
                new UpdateUserCommand(ADMIN, "E14", null, null, null, Map.of("NETWORK", 3), null, null, null)));
        assertThrows(InvalidInputException.class, () -> service.updateUser(
                new UpdateUserCommand(ADMIN, "E14", null, null, null, null, 5, null, null)));
        assertThrows(InvalidInputException.class, () -> service.updateUser(
                new UpdateUserCommand(ADMIN, "T15", null, null, "Finance", null, null, null, null)));
        assertThrows(InvalidInputException.class, () -> service.updateUser(
                new UpdateUserCommand(ADMIN, ADMIN, null, null, null, null, 5, null, null)));
    }

    @Test
    void updateUser_unknownUser_isNotFound() {
        assertThrows(UserNotFoundException.class, () -> service.updateUser(
                new UpdateUserCommand(ADMIN, "NO-SUCH", "x", null, null, null, null, null, null)));
    }

    // ---------- technician availability ----------

    @Test
    void setTechnicianAvailability_updatesAndPersistsTheFlag() {
        service.createUser(technician("T16", Role.TECHNICIAN, Map.of("NETWORK", 3)));

        Technician off = service.setTechnicianAvailability(new SetTechnicianAvailabilityCommand(ADMIN, "T16", false));

        assertFalse(off.isAvailable());
        assertFalse(((Technician) users.findById("T16").orElseThrow()).isAvailable());

        Technician on = service.setTechnicianAvailability(new SetTechnicianAvailabilityCommand(ADMIN, "T16", true));

        assertTrue(on.isAvailable());
        assertTrue(((Technician) users.findById("T16").orElseThrow()).isAssignable());
    }

    @Test
    void setTechnicianAvailability_nonTechnicianOrUnknown_isRejected() {
        service.createUser(employee("E15"));

        assertThrows(InvalidInputException.class, () -> service.setTechnicianAvailability(
                new SetTechnicianAvailabilityCommand(ADMIN, "E15", false)));
        assertThrows(UserNotFoundException.class, () -> service.setTechnicianAvailability(
                new SetTechnicianAvailabilityCommand(ADMIN, "NO-SUCH", false)));
        assertThrows(InvalidInputException.class,
                () -> new SetTechnicianAvailabilityCommand(ADMIN, " ", true));
    }

    @Test
    void constructor_nullDependency_throwsInvalidInputException() {
        assertThrows(InvalidInputException.class, () ->
                new UserAdminService(null, categories, new AdminPermissionPolicy(), userFactory, authService));
        assertThrows(InvalidInputException.class, () ->
                new UserAdminService(users, null, new AdminPermissionPolicy(), userFactory, authService));
        assertThrows(InvalidInputException.class, () ->
                new UserAdminService(users, categories, null, userFactory, authService));
        assertThrows(InvalidInputException.class, () ->
                new UserAdminService(users, categories, new AdminPermissionPolicy(), null, authService));
        assertThrows(InvalidInputException.class, () ->
                new UserAdminService(users, categories, new AdminPermissionPolicy(), userFactory, null));
    }
}
