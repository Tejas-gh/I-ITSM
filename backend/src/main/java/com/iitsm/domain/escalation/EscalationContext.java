package com.iitsm.domain.escalation;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import com.iitsm.domain.incident.Incident;

/**
 * Context passed along the Chain of Responsibility for evaluating whether an unacknowledged
 * P1 incident qualifies for escalation.
 *
 * @param incident               the target incident
 * @param responseTargetDuration the calculated response target duration (between created and response deadline)
 * @param now                    current reference evaluation timestamp
 * @param existingLevels         set of escalation levels already recorded for this incident
 */
public record EscalationContext(
        Incident incident,
        Duration responseTargetDuration,
        Instant now,
        Set<Integer> existingLevels
) {
    public EscalationContext {
        Objects.requireNonNull(incident, "incident must not be null");
        Objects.requireNonNull(responseTargetDuration, "responseTargetDuration must not be null");
        Objects.requireNonNull(now, "now must not be null");
        existingLevels = existingLevels != null ? Set.copyOf(existingLevels) : Set.of();
    }
}
