package com.iitsm.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import com.iitsm.application.security.AccessToken;
import com.iitsm.application.security.UserCredentials;
import com.iitsm.application.service.AuthService;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.factory.UserFactory;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Role;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.FakeCredentialsRepository;
import com.iitsm.support.FakePasswordHasher;

class BootstrapAdminInitializerTest {

    private static final String PASSWORD = "Bootstrap-Test-Pass-1";

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final FakeCredentialsRepository credentials = new FakeCredentialsRepository();
    private final FakePasswordHasher hasher = new FakePasswordHasher();
    private final AuthService authService = new AuthService(users, credentials, hasher,
            user -> new AccessToken("t", Instant.parse("2030-01-01T00:00:00Z")), new UserFactory());

    private BootstrapAdminInitializer initializerWith(String id, String name, String email, String password) {
        return new BootstrapAdminInitializer(authService, new BootstrapAdminProperties(id, name, email, password),
                TransactionOperations.withoutTransaction());
    }

    private BootstrapAdminInitializer fullyConfigured() {
        return initializerWith("ROOT", "Root Admin", "root@corp.com", PASSWORD);
    }

    @Test
    void bootstrap_noAdministrator_createsTheConfiguredAdministrator() {
        fullyConfigured().bootstrap();

        Administrator admin = assertInstanceOf(Administrator.class, users.findById("ROOT").orElseThrow());
        assertEquals(Role.ADMINISTRATOR, admin.getRole());
        assertEquals("Root Admin", admin.getName());
        assertEquals("root@corp.com", admin.getEmail());
        assertTrue(admin.isActive());
    }

    @Test
    void bootstrap_storesOnlyAHashOfThePassword() {
        fullyConfigured().bootstrap();

        UserCredentials stored = credentials.findByUserId("ROOT").orElseThrow();

        assertNotEquals(PASSWORD, stored.passwordHash());
        assertEquals(hasher.hash(PASSWORD), stored.passwordHash());
    }

    @Test
    void bootstrap_calledRepeatedly_neverCreatesDuplicates() {
        BootstrapAdminInitializer initializer = fullyConfigured();

        initializer.bootstrap();
        initializer.bootstrap();
        initializer.bootstrap();

        assertEquals(1, users.findAll().size());
        assertEquals(1, credentials.size());
    }

    @Test
    void run_startupEntryPoint_performsTheBootstrap() {
        fullyConfigured().run(null);

        assertTrue(users.findById("ROOT").isPresent());
    }

    @Test
    void bootstrap_existingAdministrator_isNotOverwrittenAndConfigIsIgnored() {
        users.save(new Administrator("EXISTING", "Existing Admin", "existing@corp.com", Role.ADMINISTRATOR));
        credentials.save(new UserCredentials("EXISTING", "existing-hash"));

        fullyConfigured().bootstrap();

        assertEquals(1, users.findAll().size());
        assertTrue(users.findById("ROOT").isEmpty());
        assertEquals("Existing Admin", users.findById("EXISTING").orElseThrow().getName());
        assertEquals("existing-hash", credentials.findByUserId("EXISTING").orElseThrow().passwordHash());
    }

    @Test
    void bootstrap_existingAdministratorAndNoPassword_doesNothingAtAll() {
        users.save(new Administrator("EXISTING", "Existing Admin", "existing@corp.com", Role.ADMINISTRATOR));

        initializerWith(null, null, null, null).bootstrap();

        assertEquals(1, users.findAll().size());
    }

    @Test
    void bootstrap_missingOrBlankPassword_createsNothing() {
        initializerWith("ROOT", "Root Admin", "root@corp.com", null).bootstrap();
        initializerWith("ROOT", "Root Admin", "root@corp.com", "").bootstrap();
        initializerWith("ROOT", "Root Admin", "root@corp.com", "   ").bootstrap();

        assertTrue(users.findAll().isEmpty());
        assertEquals(0, credentials.size());
    }

    @Test
    void bootstrap_missingIdentityValue_createsNothing() {
        initializerWith(null, "Root Admin", "root@corp.com", PASSWORD).bootstrap();
        initializerWith("ROOT", " ", "root@corp.com", PASSWORD).bootstrap();
        initializerWith("ROOT", "Root Admin", "", PASSWORD).bootstrap();

        assertTrue(users.findAll().isEmpty());
        assertEquals(0, credentials.size());
    }

    @Test
    void bootstrap_unacceptablePassword_failsLoudlyAndCreatesNothing() {
        BootstrapAdminInitializer initializer = initializerWith("ROOT", "Root Admin", "root@corp.com", "short");

        assertThrows(InvalidInputException.class, initializer::bootstrap);

        assertTrue(users.findAll().isEmpty());
        assertEquals(0, credentials.size());
    }

    @Test
    void bootstrap_configuredIdBelongsToAnExistingEmployee_failsAndKeepsTheEmployee() {
        users.save(new Employee("ROOT", "Not An Admin", "emp@corp.com", "IT"));

        assertThrows(InvalidInputException.class, fullyConfigured()::bootstrap);

        assertEquals(Role.EMPLOYEE, users.findById("ROOT").orElseThrow().getRole());
    }

    @Test
    void properties_toString_doesNotRevealThePassword() {
        BootstrapAdminProperties properties =
                new BootstrapAdminProperties("ROOT", "Root Admin", "root@corp.com", PASSWORD);

        assertFalse(properties.toString().contains(PASSWORD));
        assertTrue(properties.toString().contains("ROOT"));
    }
}
