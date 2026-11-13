package com.iitsm.api.dto;

import com.iitsm.domain.audit.AuditEntry;
import com.iitsm.domain.model.IncidentStatus;

import java.time.Instant;

/**
 * Representation of an audit log entry returned over HTTP.
 */
public record AuditEntryResponse(
        Instant occurredAt,
        String actorId,
        String incidentId,
        IncidentStatus previousStatus,
        IncidentStatus newStatus,
        String action,
        String description
) {
    public static AuditEntryResponse from(AuditEntry entry) {
        return new AuditEntryResponse(
                entry.occurredAt(),
                entry.actorId(),
                entry.incidentId(),
                entry.previousStatus(),
                entry.newStatus(),
                entry.action(),
                entry.description()
        );
    }
}
