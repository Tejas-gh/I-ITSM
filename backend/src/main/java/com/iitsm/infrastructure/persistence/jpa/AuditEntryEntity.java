package com.iitsm.infrastructure.persistence.jpa;

import java.time.Instant;

import com.iitsm.domain.audit.AuditEntry;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.IncidentStatus;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA structure for {@code audit_entries}. {@code actorId} has no FK (SYSTEM is a valid actor). */
@Entity
@Table(name = "audit_entries")
public class AuditEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Instant occurredAt;
    String actorId;
    String incidentId;
    @Enumerated(EnumType.STRING)
    IncidentStatus previousStatus;
    @Enumerated(EnumType.STRING)
    IncidentStatus newStatus;
    String action;
    String description;

    protected AuditEntryEntity() {
    }

    static AuditEntryEntity from(AuditEntry entry) {
        if (entry.newStatus() == null) {
            // audit_entries.new_status is NOT NULL (A3); every DomainEvent carries a non-null newStatus (§P.1)
            throw new InvalidInputException("newStatus must not be null for a persisted audit entry");
        }
        AuditEntryEntity e = new AuditEntryEntity();
        e.occurredAt = entry.occurredAt();
        e.actorId = entry.actorId();
        e.incidentId = entry.incidentId();
        e.previousStatus = entry.previousStatus();
        e.newStatus = entry.newStatus();
        e.action = entry.action();
        e.description = entry.description();
        return e;
    }

    AuditEntry toDomain() {
        return new AuditEntry(occurredAt, actorId, incidentId, previousStatus, newStatus, action, description);
    }
}
