package com.iitsm.infrastructure.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.iitsm.application.security.PasswordHasher;
import com.iitsm.domain.exception.InvalidInputException;

/** {@link PasswordHasher} backed by BCrypt (salted, so hashing the same password twice gives different hashes). */
public final class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        if (rawPassword == null) {
            throw new InvalidInputException("password must not be null");
        }
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null) {
            throw new InvalidInputException("password must not be null");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        return encoder.matches(rawPassword, passwordHash);
    }
}
