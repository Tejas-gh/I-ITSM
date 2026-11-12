package com.iitsm.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.iitsm.domain.exception.InvalidInputException;

class BCryptPasswordHasherTest {

    private static final String PASSWORD = "Correct-Horse-1";

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void hash_returnsABcryptHashThatIsNotThePlaintext() {
        String hash = hasher.hash(PASSWORD);

        assertNotEquals(PASSWORD, hash);
        assertFalse(hash.contains(PASSWORD));
        assertTrue(hash.startsWith("$2"), "expected a BCrypt hash but was: " + hash.substring(0, 4));
        assertEquals(60, hash.length());
    }

    @Test
    void hash_samePasswordTwice_givesDifferentHashesBecauseOfTheSalt() {
        assertNotEquals(hasher.hash(PASSWORD), hasher.hash(PASSWORD));
    }

    @Test
    void matches_correctPassword_isTrue() {
        assertTrue(hasher.matches(PASSWORD, hasher.hash(PASSWORD)));
    }

    @Test
    void matches_incorrectPassword_isFalse() {
        assertFalse(hasher.matches("Wrong-Password-1", hasher.hash(PASSWORD)));
        assertFalse(hasher.matches(PASSWORD.toLowerCase(), hasher.hash(PASSWORD)));
    }

    @Test
    void matches_missingOrMalformedHash_isFalse() {
        assertFalse(hasher.matches(PASSWORD, null));
        assertFalse(hasher.matches(PASSWORD, ""));
        assertFalse(hasher.matches(PASSWORD, "not-a-bcrypt-hash"));
        assertFalse(hasher.matches(PASSWORD, PASSWORD));
    }

    @Test
    void nullPassword_throwsInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> hasher.hash(null));
        assertThrows(InvalidInputException.class, () -> hasher.matches(null, hasher.hash(PASSWORD)));
    }
}
