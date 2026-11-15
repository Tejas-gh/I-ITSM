package com.iitsm.api.dto;

import java.time.Instant;

import com.iitsm.domain.sla.SlaAlert;
import com.iitsm.domain.sla.SlaAlertKind;
import com.iitsm.domain.sla.SlaType;

/**
 * REST response DTO for an SLA alert (warning or violation).
 */
public record SlaAlertResponse(
        String id,
        String incidentId,
        SlaType slaType,
        SlaAlertKind kind,
        Instant deadline,
        Instant raisedAt
) {
    public static SlaAlertResponse from(SlaAlert alert) {
        return new SlaAlertResponse(
                alert.id(),
                alert.incidentId(),
                alert.slaType(),
                alert.kind(),
                alert.deadline(),
                alert.raisedAt()
        );
    }
}
