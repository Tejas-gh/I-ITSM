package com.iitsm.domain.escalation;

import java.time.Instant;
import java.util.Objects;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Role;

/**
 * Domain record capturing an immutable escalation event for an incident.
 *
 * @param id           optional synthetic id (assigned by repository)
 * @param incidentId   the escalated incident id (mandatory)
 * @param level        the escalation level: 1, 2, or 3
 * @param targetRole   the target recipient role (mandatory)
 * @param escalatedAt  the timestamp when escalation triggered (mandatory)
 * @param reason       the business justification / explanation (mandatory)
 */
public record EscalationRecord(
        Long id,
        String incidentId,
        int level,
        Role targetRole,
        Instant escalatedAt,
        String reason
) {

    public EscalationRecord {
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be blank");
        }
        if (level < 1 || level > 3) {
            throw new InvalidInputException("level must be 1, 2, or 3");
        }
        if (targetRole == null) {
            throw new InvalidInputException("targetRole must not be null");
        }
        if (escalatedAt == null) {
            throw new InvalidInputException("escalatedAt must not be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidInputException("reason must not be blank");
        }
    }

    /**
     * Factory constructor for creating a new record before persistence without an ID.
     */
    public static EscalationRecord create(String incidentId, int level, Role targetRole, Instant escalatedAt, String reason) {
        return new EscalationRecord(null, incidentId, level, targetRole, escalatedAt, reason);
    }
}
