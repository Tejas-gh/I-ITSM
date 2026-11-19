package com.iitsm.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iitsm.domain.event.DomainEventPublisher;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.sla.SlaAlert;
import com.iitsm.domain.sla.SlaAlertKind;
import com.iitsm.domain.sla.SlaAlertRepository;
import com.iitsm.domain.sla.SlaEvaluator;

/**
 * Service responsible for scanning open incidents, evaluating SLA thresholds and deadlines,
 * idempotently recording alerts, and publishing domain events.
 */
public class SlaMonitorService {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitorService.class);

    private final IncidentRepository incidents;
    private final SlaAlertRepository slaAlerts;
    private final SlaIncidentProcessor processor;
    private final EscalationService escalationService;
    private final Clock clock;

    public SlaMonitorService(IncidentRepository incidents,
                             SlaAlertRepository slaAlerts,
                             SlaEvaluator slaEvaluator,
                             DomainEventPublisher events,
                             Clock clock) {
        this(incidents, slaAlerts, new SlaIncidentProcessor(slaAlerts, slaEvaluator, events), null, clock);
    }

    public SlaMonitorService(IncidentRepository incidents,
                             SlaAlertRepository slaAlerts,
                             SlaIncidentProcessor processor,
                             Clock clock) {
        this(incidents, slaAlerts, processor, null, clock);
    }

    public SlaMonitorService(IncidentRepository incidents,
                             SlaAlertRepository slaAlerts,
                             SlaIncidentProcessor processor,
                             EscalationService escalationService,
                             Clock clock) {
        this.incidents = Objects.requireNonNull(incidents, "incidents must not be null");
        this.slaAlerts = Objects.requireNonNull(slaAlerts, "slaAlerts must not be null");
        this.processor = Objects.requireNonNull(processor, "processor must not be null");
        this.escalationService = escalationService;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Executes an SLA monitor pass at the current wall-clock time.
     *
     * @return summary metrics of the monitor execution
     */
    public SlaMonitorResult runMonitor() {
        return runMonitor(clock.instant());
    }

    /**
     * Executes an SLA monitor pass evaluated against the given instant.
     *
     * @param now the reference timestamp
     * @return summary metrics of the monitor execution
     */
    public SlaMonitorResult runMonitor(Instant now) {
        Objects.requireNonNull(now, "now must not be null");

        List<Incident> allIncidents = incidents.findAll();
        int scanned = 0;
        int warnings = 0;
        int violations = 0;
        int errors = 0;

        for (Incident incident : allIncidents) {
            // Ignore CLOSED incidents
            if (incident == null || incident.getStatus() == IncidentStatus.CLOSED) {
                continue;
            }

            scanned++;
            try {
                IncidentSlaResult res = processor.processIncident(incident, now);
                warnings += res.warningsRaised();
                violations += res.violationsRaised();

                if (escalationService != null) {
                    escalationService.evaluateIncident(incident, now);
                }
            } catch (Exception e) {
                errors++;
                log.warn("Failed to process SLA for incident {}: {}", incident.getId(), e.getMessage());
            }
        }

        return new SlaMonitorResult(scanned, warnings, violations, errors);
    }

    /**
     * Retrieves all recorded SLA breach violations.
     */
    public List<SlaAlert> getViolations() {
        return slaAlerts.findByKind(SlaAlertKind.VIOLATION);
    }

    /**
     * Retrieves all active SLA warning/at-risk alerts.
     */
    public List<SlaAlert> getAtRiskAlerts() {
        return slaAlerts.findByKind(SlaAlertKind.WARNING);
    }

    /**
     * Retrieves all recorded alerts.
     */
    public List<SlaAlert> getAllAlerts() {
        return slaAlerts.findAll();
    }

    /**
     * Retrieves all alerts associated with a specific incident.
     */
    public List<SlaAlert> getAlertsForIncident(String incidentId) {
        return slaAlerts.findByIncidentId(incidentId);
    }
}
