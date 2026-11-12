package com.iitsm.application.security;

/** One-way password hashing, so the application layer does not depend on a security framework. */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
