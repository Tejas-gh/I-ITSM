package com.iitsm.domain.event;

import com.iitsm.domain.model.IncidentStatus;
import java.time.Instant;

/**
 * Domain event contract representing a state change or significant business event.
 * Covers the 7 audit fields: timestamp, actorId, incidentId, previousStatus,
 * newStatus, action, and description.
 */
public interface DomainEvent {

    String SYSTEM_ACTOR = "SYSTEM";

    Instant occurredAt();

    String actorId();

    String incidentId();

    IncidentStatus previousStatus();

    IncidentStatus newStatus();

    String action();

    String description();
}
