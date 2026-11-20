package com.iitsm.infrastructure.persistence.jpa;

import java.time.Instant;

import com.iitsm.domain.escalation.EscalationRecord;
import com.iitsm.domain.model.Role;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA structure for {@code escalations} (V7); {@code id} is the identity key and the insertion order. */
@Entity
@Table(name = "escalations")
public class EscalationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String incidentId;
    int escalationLevel;
    @Enumerated(EnumType.STRING)
    Role targetRole;
    Instant escalatedAt;
    String reason;

    protected EscalationEntity() {
    }

    /** Always a new row: the database assigns the id. */
    static EscalationEntity from(EscalationRecord record) {
        EscalationEntity e = new EscalationEntity();
        e.incidentId = record.incidentId();
        e.escalationLevel = record.level();
        e.targetRole = record.targetRole();
        e.escalatedAt = record.escalatedAt();
        e.reason = record.reason();
        return e;
    }

    EscalationRecord toDomain() {
        return new EscalationRecord(id, incidentId, escalationLevel, targetRole, escalatedAt, reason);
    }
}
