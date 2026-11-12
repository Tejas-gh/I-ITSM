package com.iitsm.support;

import com.iitsm.application.security.PasswordHasher;

/** Deterministic stand-in for BCrypt in plain unit tests: the "hash" is visibly different from the password. */
public final class FakePasswordHasher implements PasswordHasher {

    public static final String PREFIX = "fake-hash:";

    @Override
    public String hash(String rawPassword) {
        return PREFIX + new StringBuilder(rawPassword).reverse();
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return hash(rawPassword).equals(passwordHash);
    }
}
