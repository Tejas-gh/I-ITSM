package com.iitsm.domain.model;

import com.iitsm.domain.exception.InvalidInputException;

import java.util.Objects;

public abstract class User {

    private final String id;
    private String name;
    private String email;
    private final Role role;
    private boolean active = true;

    protected User(String id, String name, String email, Role role) {
        this.id = requireNonBlank(id, "id");
        this.name = requireNonBlank(name, "name");
        this.email = requireValidEmail(email);
        if (role == null) {
            throw new InvalidInputException("role must not be null");
        }
        this.role = role;
    }

    public final String getId() {
        return id;
    }

    public final String getName() {
        return name;
    }

    public final String getEmail() {
        return email;
    }

    public final Role getRole() {
        return role;
    }

    public final boolean isActive() {
        return active;
    }

    public final void activate() {
        this.active = true;
    }

    public final void deactivate() {
        this.active = false;
    }

    static String requireNonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidInputException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String requireValidEmail(String value) {
        String trimmed = requireNonBlank(value, "email");
        int at = trimmed.indexOf('@');
        if (at < 0 || at != trimmed.lastIndexOf('@')) {
            throw new InvalidInputException("email must contain exactly one '@'");
        }
        String local = trimmed.substring(0, at);
        String domain = trimmed.substring(at + 1);
        if (local.trim().isEmpty() || domain.trim().isEmpty()) {
            throw new InvalidInputException("email must have text on both sides of '@'");
        }
        return trimmed;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return id.equals(((User) o).id);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return role + " " + id;
    }
}
