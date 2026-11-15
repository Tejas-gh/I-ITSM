package com.iitsm.application.service;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Role;

/**
 * Value object representing a ranked technician recommendation for an incident.
 */
public record TechnicianRecommendation(
        String technicianId,
        String name,
        String email,
        Role role,
        double score,
        String reason,
        int activeWorkload,
        int maxCapacity,
        Map<String, Integer> skillLevels,
        int yearsOfExperience
) {
    public TechnicianRecommendation {
        if (technicianId == null || technicianId.isBlank()) {
            throw new InvalidInputException("technicianId must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("name must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new InvalidInputException("email must not be blank");
        }
        if (role == null) {
            throw new InvalidInputException("role must not be null");
        }
        if (Double.isNaN(score)) {
            throw new InvalidInputException("score must not be NaN");
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidInputException("reason must not be blank");
        }
        if (activeWorkload < 0) {
            throw new InvalidInputException("activeWorkload must be >= 0");
        }
        if (maxCapacity < 1) {
            throw new InvalidInputException("maxCapacity must be >= 1");
        }
        if (yearsOfExperience < 0) {
            throw new InvalidInputException("yearsOfExperience must be >= 0");
        }
        skillLevels = skillLevels != null ? Collections.unmodifiableMap(skillLevels) : Collections.emptyMap();
    }
}
