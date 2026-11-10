package com.iitsm.domain.model;

import com.iitsm.domain.exception.InvalidInputException;

import java.util.Objects;

public final class ItService {

    private final String id;
    private final String name;
    private final BusinessCriticality businessCriticality;

    public ItService(String id, String name, BusinessCriticality businessCriticality) {
        this.id = requireNonBlank(id, "id");
        this.name = requireNonBlank(name, "name");
        if (businessCriticality == null) {
            throw new InvalidInputException("businessCriticality must not be null");
        }
        this.businessCriticality = businessCriticality;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BusinessCriticality getBusinessCriticality() {
        return businessCriticality;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidInputException(field + " must not be blank");
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return id.equals(((ItService) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
