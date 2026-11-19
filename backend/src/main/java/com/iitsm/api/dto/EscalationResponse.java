package com.iitsm.api.dto;

import java.time.Instant;

import com.iitsm.domain.escalation.EscalationRecord;
import com.iitsm.domain.model.Role;

/**
 * REST response representing an escalation record.
 */
public record EscalationResponse(
        Long id,
        String incidentId,
        int level,
        Role targetRole,
        Instant escalatedAt,
        String reason
) {
    public static EscalationResponse from(EscalationRecord record) {
        return new EscalationResponse(
                record.id(),
                record.incidentId(),
                record.level(),
                record.targetRole(),
                record.escalatedAt(),
                record.reason()
        );
    }
}
