package com.iitsm.application.service;

import java.time.Instant;
import java.util.Objects;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.domain.event.DomainEventPublisher;
import com.iitsm.domain.event.SlaViolated;
import com.iitsm.domain.event.SlaWarning;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.sla.SlaAlert;
import com.iitsm.domain.sla.SlaAlertKind;
import com.iitsm.domain.sla.SlaAlertRepository;
import com.iitsm.domain.sla.SlaEvaluation;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.domain.sla.SlaStatus;
import com.iitsm.domain.sla.SlaType;

/**
 * Evaluates and records SLA alerts for an individual incident in an isolated transaction.
 */
public class SlaIncidentProcessor {

    public static final String SYSTEM_ACTOR_ID = "SYSTEM";

    private final SlaAlertRepository slaAlerts;
    private final SlaEvaluator slaEvaluator;
    private final DomainEventPublisher events;

    public SlaIncidentProcessor(SlaAlertRepository slaAlerts,
                                SlaEvaluator slaEvaluator,
                                DomainEventPublisher events) {
        this.slaAlerts = Objects.requireNonNull(slaAlerts, "slaAlerts must not be null");
        this.slaEvaluator = Objects.requireNonNull(slaEvaluator, "slaEvaluator must not be null");
        this.events = Objects.requireNonNull(events, "events must not be null");
    }

    /**
     * Evaluates SLA deadlines for the incident against {@code now}, records alerts idempotently,
     * and publishes domain events.
     *
     * @param incident the incident to evaluate; skipped if null or CLOSED
     * @param now the current evaluation instant
     * @return the count of warnings and violations raised in this evaluation
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IncidentSlaResult processIncident(Incident incident, Instant now) {
        if (incident == null || incident.getStatus() == IncidentStatus.CLOSED) {
            return IncidentSlaResult.EMPTY;
        }
        if (incident.getResponseDeadline().isEmpty() || incident.getResolutionDeadline().isEmpty()) {
            return IncidentSlaResult.EMPTY;
        }

        SlaEvaluation eval = slaEvaluator.evaluate(incident, now);
        int warnings = 0;
        int violations = 0;

        // 1. Response SLA
        if (eval.response() == SlaStatus.BREACHED) {
            if (!slaAlerts.exists(incident.getId(), SlaType.RESPONSE, SlaAlertKind.VIOLATION)) {
                SlaAlert alert = new SlaAlert(incident.getId(), SlaType.RESPONSE, SlaAlertKind.VIOLATION,
                        eval.effectiveResponseDeadline(), now);
                slaAlerts.save(alert);
                events.publish(new SlaViolated(
                        now,
                        SYSTEM_ACTOR_ID,
                        incident.getId(),
                        incident.getStatus(),
                        incident.getStatus(),
                        "Response SLA breached",
                        SlaType.RESPONSE,
                        eval.effectiveResponseDeadline()
                ));
                violations++;
            }
        } else if (eval.response() == SlaStatus.WARNING) {
            if (!slaAlerts.exists(incident.getId(), SlaType.RESPONSE, SlaAlertKind.WARNING)) {
                SlaAlert alert = new SlaAlert(incident.getId(), SlaType.RESPONSE, SlaAlertKind.WARNING,
                        eval.effectiveResponseDeadline(), now);
                slaAlerts.save(alert);
                events.publish(new SlaWarning(
                        now,
                        SYSTEM_ACTOR_ID,
                        incident.getId(),
                        incident.getStatus(),
                        incident.getStatus(),
                        "Response SLA warning (<= 20% remaining)",
                        SlaType.RESPONSE,
                        eval.effectiveResponseDeadline()
                ));
                warnings++;
            }
        }

        // 2. Resolution SLA
        if (eval.resolution() == SlaStatus.BREACHED) {
            if (!slaAlerts.exists(incident.getId(), SlaType.RESOLUTION, SlaAlertKind.VIOLATION)) {
                SlaAlert alert = new SlaAlert(incident.getId(), SlaType.RESOLUTION, SlaAlertKind.VIOLATION,
                        eval.effectiveResolutionDeadline(), now);
                slaAlerts.save(alert);
                events.publish(new SlaViolated(
                        now,
                        SYSTEM_ACTOR_ID,
                        incident.getId(),
                        incident.getStatus(),
                        incident.getStatus(),
                        "Resolution SLA breached",
                        SlaType.RESOLUTION,
                        eval.effectiveResolutionDeadline()
                ));
                violations++;
            }
        } else if (eval.resolution() == SlaStatus.WARNING) {
            if (!slaAlerts.exists(incident.getId(), SlaType.RESOLUTION, SlaAlertKind.WARNING)) {
                SlaAlert alert = new SlaAlert(incident.getId(), SlaType.RESOLUTION, SlaAlertKind.WARNING,
                        eval.effectiveResolutionDeadline(), now);
                slaAlerts.save(alert);
                events.publish(new SlaWarning(
                        now,
                        SYSTEM_ACTOR_ID,
                        incident.getId(),
                        incident.getStatus(),
                        incident.getStatus(),
                        "Resolution SLA warning (<= 20% remaining)",
                        SlaType.RESOLUTION,
                        eval.effectiveResolutionDeadline()
                ));
                warnings++;
            }
        }

        return new IncidentSlaResult(warnings, violations);
    }
}
