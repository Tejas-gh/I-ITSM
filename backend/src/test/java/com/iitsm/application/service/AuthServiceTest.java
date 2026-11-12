package com.iitsm.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.iitsm.application.command.LoginCommand;
import com.iitsm.application.command.RegisterEmployeeCommand;
import com.iitsm.application.security.AccessToken;
import com.iitsm.application.security.TokenIssuer;
import com.iitsm.application.security.UserCredentials;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.InvalidLoginException;
import com.iitsm.domain.factory.UserFactory;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.User;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.FakeCredentialsRepository;
import com.iitsm.support.FakePasswordHasher;

class AuthServiceTest {

    private static final String PASSWORD = "Correct-Horse-1";

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final FakeCredentialsRepository credentials = new FakeCredentialsRepository();
    private final FakePasswordHasher hasher = new FakePasswordHasher();
    private final TokenIssuer tokens = user -> new AccessToken("token-for-" + user.getId(), Instant.parse("2030-01-01T00:00:00Z"));
    private final AuthService service = new AuthService(users, credentials, hasher, tokens, new UserFactory());

    private Employee registerAlice() {
        return service.registerEmployee(
                new RegisterEmployeeCommand("E100", "Alice", "alice@corp.com", "Finance", PASSWORD));
    }

    // ---------- authentication ----------

    @Test
    void authenticate_validCredentials_returnsTheUser() {
        registerAlice();

        User user = service.authenticate(new LoginCommand("E100", PASSWORD));

        assertEquals("E100", user.getId());
        assertEquals(Role.EMPLOYEE, user.getRole());
    }

    @Test
    void login_validCredentials_returnsTokenForThatUser() {
        registerAlice();

        AccessToken token = service.login(new LoginCommand("E100", PASSWORD));

        assertEquals("token-for-E100", token.value());
    }

    @Test
    void authenticate_idWithSurroundingSpaces_isTrimmed() {
        registerAlice();

        assertEquals("E100", service.authenticate(new LoginCommand("  E100 ", PASSWORD)).getId());
    }

    @Test
    void authenticate_wrongPassword_throwsInvalidLoginException() {
        registerAlice();

        InvalidLoginException ex = assertThrows(InvalidLoginException.class,
                () -> service.authenticate(new LoginCommand("E100", "Wrong-Password-1")));
        assertEquals("INVALID_LOGIN", ex.getCode());
    }

    @Test
    void authenticate_unknownUser_throwsInvalidLoginException() {
        assertThrows(InvalidLoginException.class,
                () -> service.authenticate(new LoginCommand("NOBODY", PASSWORD)));
    }

    @Test
    void authenticate_userWithoutCredentials_throwsInvalidLoginException() {
        users.save(new Employee("E200", "Bob", "bob@corp.com", "IT"));

        assertThrows(InvalidLoginException.class,
                () -> service.authenticate(new LoginCommand("E200", PASSWORD)));
    }

    @Test
    void authenticate_inactiveUser_throwsInvalidLoginException() {
        Employee alice = registerAlice();
        alice.deactivate();
        users.save(alice);

        assertThrows(InvalidLoginException.class,
                () -> service.authenticate(new LoginCommand("E100", PASSWORD)));
    }

    @Test
    void authenticate_failureMessages_doNotRevealWhetherTheUserExists() {
        registerAlice();

        String unknownUser = assertThrows(InvalidLoginException.class,
                () -> service.authenticate(new LoginCommand("NOBODY", PASSWORD))).getMessage();
        String wrongPassword = assertThrows(InvalidLoginException.class,
                () -> service.authenticate(new LoginCommand("E100", "Wrong-Password-1"))).getMessage();

        assertEquals(unknownUser, wrongPassword);
    }

    @Test
    void loginCommand_blankIdOrEmptyPassword_throwsInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> new LoginCommand(" ", PASSWORD));
        assertThrows(InvalidInputException.class, () -> new LoginCommand(null, PASSWORD));
        assertThrows(InvalidInputException.class, () -> new LoginCommand("E100", ""));
        assertThrows(InvalidInputException.class, () -> new LoginCommand("E100", null));
    }

    // ---------- registration ----------

    @Test
    void registerEmployee_validData_createsAnActiveEmployee() {
        Employee employee = registerAlice();

        assertEquals(Role.EMPLOYEE, employee.getRole());
        assertEquals("Finance", employee.getDepartment());
        assertTrue(employee.isActive());
        assertInstanceOf(Employee.class, users.findById("E100").orElseThrow());
    }

    @Test
    void registerEmployee_storesTheHashNeverThePlaintextPassword() {
        registerAlice();

        UserCredentials stored = credentials.findByUserId("E100").orElseThrow();

        assertNotEquals(PASSWORD, stored.passwordHash());
        assertEquals(hasher.hash(PASSWORD), stored.passwordHash());
        assertFalse(stored.toString().contains(stored.passwordHash()));
    }

    @Test
    void registerEmployee_duplicateId_isRejectedAndKeepsTheOriginalAccount() {
        registerAlice();
        String originalHash = credentials.findByUserId("E100").orElseThrow().passwordHash();

        assertThrows(InvalidInputException.class, () -> service.registerEmployee(
                new RegisterEmployeeCommand("E100", "Mallory", "mallory@corp.com", "IT", "Another-Pass-9")));

        assertEquals("Alice", users.findById("E100").orElseThrow().getName());
        assertEquals(originalHash, credentials.findByUserId("E100").orElseThrow().passwordHash());
    }

    @Test
    void registerEmployee_unacceptablePassword_isRejectedAndSavesNothing() {
        String seventyThreeBytes = "x".repeat(AuthService.MAX_PASSWORD_BYTES + 1);

        for (String bad : new String[] {null, "", "   ", "short7!", seventyThreeBytes}) {
            assertThrows(InvalidInputException.class, () -> service.registerEmployee(
                    new RegisterEmployeeCommand("E300", "Carol", "carol@corp.com", "HR", bad)));
        }

        assertTrue(users.findById("E300").isEmpty());
        assertEquals(0, credentials.size());
    }

    @Test
    void registerEmployee_passwordAtBoundaryLengths_isAccepted() {
        service.registerEmployee(new RegisterEmployeeCommand("E301", "Dan", "dan@corp.com", "HR",
                "x".repeat(AuthService.MIN_PASSWORD_LENGTH)));
        service.registerEmployee(new RegisterEmployeeCommand("E302", "Eve", "eve@corp.com", "HR",
                "x".repeat(AuthService.MAX_PASSWORD_BYTES)));

        assertEquals(2, credentials.size());
    }

    @Test
    void registerEmployee_invalidUserData_isRejectedAndSavesNothing() {
        assertThrows(InvalidInputException.class, () -> service.registerEmployee(
                new RegisterEmployeeCommand("E400", "Frank", "not-an-email", "IT", PASSWORD)));
        assertThrows(InvalidInputException.class, () -> service.registerEmployee(
                new RegisterEmployeeCommand("E401", "Gina", "gina@corp.com", null, PASSWORD)));
        assertThrows(InvalidInputException.class, () -> service.registerEmployee(
                new RegisterEmployeeCommand("E402", " ", "hal@corp.com", "IT", PASSWORD)));

        assertTrue(users.findAll().isEmpty());
        assertEquals(0, credentials.size());
    }

    @Test
    void registerEmployee_blankId_throwsInvalidInputException() {
        assertThrows(InvalidInputException.class,
                () -> new RegisterEmployeeCommand(" ", "Ivy", "ivy@corp.com", "IT", PASSWORD));
    }

    @Test
    void commands_toString_neverContainsThePassword() {
        assertFalse(new LoginCommand("E100", PASSWORD).toString().contains(PASSWORD));
        assertFalse(new RegisterEmployeeCommand("E100", "Alice", "alice@corp.com", "Finance", PASSWORD)
                .toString().contains(PASSWORD));
    }

    // ---------- first administrator ----------

    @Test
    void bootstrapAdministrator_noAdministrator_createsOneWithAHashedPassword() {
        Optional<Administrator> created =
                service.bootstrapAdministrator("ROOT", "Root Admin", "root@corp.com", PASSWORD);

        assertTrue(created.isPresent());
        assertEquals(Role.ADMINISTRATOR, created.get().getRole());
        assertTrue(users.findById("ROOT").orElseThrow().isActive());
        assertEquals(hasher.hash(PASSWORD), credentials.findByUserId("ROOT").orElseThrow().passwordHash());
        assertNotEquals(PASSWORD, credentials.findByUserId("ROOT").orElseThrow().passwordHash());
        assertEquals("ROOT", service.authenticate(new LoginCommand("ROOT", PASSWORD)).getId());
    }

    @Test
    void bootstrapAdministrator_calledTwice_createsOnlyOneAdministrator() {
        service.bootstrapAdministrator("ROOT", "Root Admin", "root@corp.com", PASSWORD);

        Optional<Administrator> second =
                service.bootstrapAdministrator("OTHER", "Other Admin", "other@corp.com", "Another-Pass-9");

        assertTrue(second.isEmpty());
        assertEquals(1, users.findAll().size());
        assertTrue(users.findById("OTHER").isEmpty());
    }

    @Test
    void bootstrapAdministrator_existingAdministrator_isNeverOverwritten() {
        users.save(new Administrator("ROOT", "Original Name", "original@corp.com", Role.ADMINISTRATOR));
        credentials.save(new UserCredentials("ROOT", "original-hash"));

        Optional<Administrator> result =
                service.bootstrapAdministrator("ROOT", "New Name", "new@corp.com", "Brand-New-Pass-1");

        assertTrue(result.isEmpty());
        assertEquals("Original Name", users.findById("ROOT").orElseThrow().getName());
        assertEquals("original-hash", credentials.findByUserId("ROOT").orElseThrow().passwordHash());
    }

    @Test
    void bootstrapAdministrator_onlyAnItManagerExists_stillCreatesAnAdministrator() {
        users.save(new Administrator("MGR", "Manager", "mgr@corp.com", Role.IT_MANAGER));

        assertTrue(service.bootstrapAdministrator("ROOT", "Root", "root@corp.com", PASSWORD).isPresent());
        assertTrue(service.administratorExists());
    }

    @Test
    void bootstrapAdministrator_idOfAnExistingNonAdmin_isRejected() {
        registerAlice();

        assertThrows(InvalidInputException.class,
                () -> service.bootstrapAdministrator("E100", "Root", "root@corp.com", PASSWORD));

        assertEquals(Role.EMPLOYEE, users.findById("E100").orElseThrow().getRole());
    }

    @Test
    void bootstrapAdministrator_weakPassword_isRejectedAndCreatesNothing() {
        assertThrows(InvalidInputException.class,
                () -> service.bootstrapAdministrator("ROOT", "Root", "root@corp.com", "short"));

        assertFalse(service.administratorExists());
        assertEquals(0, credentials.size());
    }

    @Test
    void administratorExists_emptySystem_isFalse() {
        assertFalse(service.administratorExists());
    }

    @Test
    void constructor_nullDependency_throwsInvalidInputException() {
        assertThrows(InvalidInputException.class,
                () -> new AuthService(null, credentials, hasher, tokens, new UserFactory()));
        assertThrows(InvalidInputException.class,
                () -> new AuthService(users, null, hasher, tokens, new UserFactory()));
        assertThrows(InvalidInputException.class,
                () -> new AuthService(users, credentials, null, tokens, new UserFactory()));
        assertThrows(InvalidInputException.class,
                () -> new AuthService(users, credentials, hasher, null, new UserFactory()));
        assertThrows(InvalidInputException.class,
                () -> new AuthService(users, credentials, hasher, tokens, null));
    }
}
