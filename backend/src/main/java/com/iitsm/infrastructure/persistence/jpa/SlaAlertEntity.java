package com.iitsm.infrastructure.persistence.jpa;

import java.time.Instant;

import com.iitsm.domain.sla.SlaAlert;
import com.iitsm.domain.sla.SlaAlertKind;
import com.iitsm.domain.sla.SlaType;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA structure for {@code sla_alerts} (V6); {@code seq} is the insertion-order key. */
@Entity
@Table(name = "sla_alerts")
public class SlaAlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long seq;
    String alertId;
    String incidentId;
    @Enumerated(EnumType.STRING)
    SlaType slaType;
    @Enumerated(EnumType.STRING)
    SlaAlertKind kind;
    Instant deadline;
    Instant raisedAt;

    protected SlaAlertEntity() {
    }

    static SlaAlertEntity from(SlaAlert alert) {
        SlaAlertEntity e = new SlaAlertEntity();
        e.alertId = alert.id();
        e.incidentId = alert.incidentId();
        e.slaType = alert.slaType();
        e.kind = alert.kind();
        e.copyMutableFrom(alert);
        return e;
    }

    /** Upsert on the idempotency key refreshes only the time fields; the identity and key stay as first stored. */
    void copyMutableFrom(SlaAlert alert) {
        this.deadline = alert.deadline();
        this.raisedAt = alert.raisedAt();
    }

    SlaAlert toDomain() {
        return new SlaAlert(alertId, incidentId, slaType, kind, deadline, raisedAt);
    }
}
