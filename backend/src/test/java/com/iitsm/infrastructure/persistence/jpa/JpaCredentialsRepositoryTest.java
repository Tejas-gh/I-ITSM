package com.iitsm.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.application.security.CredentialsRepository;
import com.iitsm.application.security.UserCredentials;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.repository.UserRepository;

import jakarta.persistence.EntityManager;

/** A5: credentials persistence on H2 with the Flyway schema. Each test is rolled back. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JpaCredentialsRepositoryTest {

    @Autowired
    private CredentialsRepository credentials;

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entityManager;

    private void saveUser(String id) {
        users.save(new Employee(id, "Test " + id, id.toLowerCase() + "@corp.com", "IT"));
        entityManager.flush();
    }

    @Test
    void save_thenFindByUserId_roundTrips() {
        saveUser("CRED1");
        credentials.save(new UserCredentials("CRED1", "$2a$10$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234"));
        entityManager.flush();
        entityManager.clear();

        UserCredentials found = credentials.findByUserId("CRED1").orElseThrow();

        assertEquals("CRED1", found.userId());
        assertEquals("$2a$10$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234", found.passwordHash());
    }

    @Test
    void save_sameUserTwice_replacesTheHash() {
        saveUser("CRED2");
        credentials.save(new UserCredentials("CRED2", "first-hash"));
        entityManager.flush();
        credentials.save(new UserCredentials("CRED2", "second-hash"));
        entityManager.flush();
        entityManager.clear();

        assertEquals("second-hash", credentials.findByUserId("CRED2").orElseThrow().passwordHash());
    }

    @Test
    void findByUserId_unknownUser_isEmpty() {
        assertTrue(credentials.findByUserId("NO-SUCH-USER").isEmpty());
    }

    @Test
    void nullArguments_throwInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> credentials.save(null));
        assertThrows(InvalidInputException.class, () -> credentials.findByUserId(null));
    }
}
