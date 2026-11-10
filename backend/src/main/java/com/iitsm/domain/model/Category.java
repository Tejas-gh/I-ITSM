package com.iitsm.domain.model;

import com.iitsm.domain.exception.InvalidInputException;

import java.util.Objects;
import java.util.regex.Pattern;

public final class Category {

    private static final Pattern ID_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");

    private final String id;
    private final String name;
    private boolean active = true;

    public Category(String id, String name) {
        if (id == null || !ID_PATTERN.matcher(id.trim()).matches()) {
            throw new InvalidInputException("category id must match [A-Z][A-Z0-9_]*");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidInputException("name must not be blank");
        }
        this.id = id.trim();
        this.name = name.trim();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return id.equals(((Category) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
