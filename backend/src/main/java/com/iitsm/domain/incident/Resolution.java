package com.iitsm.domain.incident;

import com.iitsm.domain.exception.InvalidInputException;

import java.time.Instant;

/**
 * Immutable record of an incident resolution summary, set when a technician resolves the incident.
 */
public record Resolution(String resolverId, String summary, Instant resolvedAt) {

    public Resolution {
        if (resolverId == null || resolverId.trim().isEmpty()) {
            throw new InvalidInputException("resolverId must not be blank");
        }
        resolverId = resolverId.trim();
        if (summary == null || summary.trim().isEmpty()) {
            throw new InvalidInputException("summary must not be blank");
        }
        summary = summary.trim();
        if (resolvedAt == null) {
            throw new InvalidInputException("resolvedAt must not be null");
        }
    }
}

