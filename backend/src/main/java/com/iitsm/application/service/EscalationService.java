package com.iitsm.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iitsm.domain.escalation.AdminEscalationHandler;
import com.iitsm.domain.escalation.EscalationContext;
import com.iitsm.domain.escalation.EscalationDecision;
import com.iitsm.domain.escalation.EscalationHandler;
import com.iitsm.domain.escalation.EscalationRecord;
import com.iitsm.domain.escalation.EscalationRepository;
import com.iitsm.domain.escalation.ItManagerEscalationHandler;
import com.iitsm.domain.escalation.TeamLeadEscalationHandler;
import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.DomainEventPublisher;
import com.iitsm.domain.event.IncidentEscalated;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.repository.IncidentRepository;

/**
 * Application service orchestrating automatic escalation for unacknowledged P1 incidents
 * using the Chain of Responsibility pattern.
 *
 * <p>Escalation chain:
 * <ul>
 *   <li>Level 1: Team Lead (at response deadline)</li>
 *   <li>Level 2: Administrator (after +1 response target)</li>
 *   <li>Level 3: IT Manager (after +2 response targets)</li>
 * </ul>
 * </p>
 */
public class EscalationService {

    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);

    private final IncidentRepository incidentRepository;
    private final EscalationRepository escalationRepository;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;
    private final EscalationHandler escalationChain;

    public EscalationService(IncidentRepository incidentRepository,
                             EscalationRepository escalationRepository,
                             DomainEventPublisher eventPublisher,
                             Clock clock) {
        this(incidentRepository, escalationRepository, eventPublisher, clock, buildDefaultChain());
    }

    public EscalationService(IncidentRepository incidentRepository,
                             EscalationRepository escalationRepository,
                             DomainEventPublisher eventPublisher,
                             Clock clock,
                             EscalationHandler escalationChain) {
        this.incidentRepository = Objects.requireNonNull(incidentRepository, "incidentRepository must not be null");
        this.escalationRepository = Objects.requireNonNull(escalationRepository, "escalationRepository must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.escalationChain = Objects.requireNonNull(escalationChain, "escalationChain must not be null");
    }

    /**
     * Builds the standard 3-tier Chain of Responsibility:
     * TeamLead (Level 1) -> Administrator (Level 2) -> ITManager (Level 3).
     */
    public static EscalationHandler buildDefaultChain() {
        TeamLeadEscalationHandler teamLead = new TeamLeadEscalationHandler();
        AdminEscalationHandler admin = new AdminEscalationHandler();
        ItManagerEscalationHandler itManager = new ItManagerEscalationHandler();

        teamLead.setNext(admin);
        admin.setNext(itManager);
        return teamLead;
    }

    /**
     * Evaluates an individual incident at the current wall-clock instant.
     */
    public Optional<EscalationRecord> evaluateIncident(Incident incident) {
        return evaluateIncident(incident, clock.instant());
    }

    /**
     * Evaluates an individual incident for escalation at a given reference timestamp.
     *
     * <p>Eligibility requirements:
     * <ul>
     *   <li>Must not be CLOSED</li>
     *   <li>Must be Priority P1</li>
     *   <li>Must still be unacknowledged (acknowledgedAt is null/empty)</li>
     *   <li>Must have a valid response deadline and positive response target duration</li>
     * </ul>
     * </p>
     *
     * @param incident the incident to evaluate
     * @param now      the evaluation reference timestamp
     * @return newly recorded EscalationRecord if escalation triggered, or empty
     */
    public Optional<EscalationRecord> evaluateIncident(Incident incident, Instant now) {
        if (incident == null || now == null) {
            return Optional.empty();
        }

        // Scope rule: skip closed tickets
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            return Optional.empty();
        }

        // Scope rule: P1 incidents only
        if (incident.getPriority() != PriorityLevel.P1) {
            return Optional.empty();
        }

        // Scope rule: Incident must still be unacknowledged
        if (incident.getAcknowledgedAt().isPresent()) {
            return Optional.empty();
        }

        // Must have response deadline
        if (incident.getResponseDeadline().isEmpty()) {
            return Optional.empty();
        }

        Instant responseDeadline = incident.getResponseDeadline().get();
        Duration responseTarget = Duration.between(incident.getCreatedAt(), responseDeadline);
        if (responseTarget.isNegative() || responseTarget.isZero()) {
            return Optional.empty();
        }

        // Retrieve previously triggered levels
        List<EscalationRecord> existingRecords = escalationRepository.findByIncidentId(incident.getId());
        Set<Integer> existingLevels = existingRecords.stream()
                .map(EscalationRecord::level)
                .collect(Collectors.toSet());

        // If Level 3 already reached, no further escalation is possible
        if (existingLevels.contains(3)) {
            return Optional.empty();
        }

        EscalationContext context = new EscalationContext(incident, responseTarget, now, existingLevels);
        Optional<EscalationDecision> decisionOpt = escalationChain.handle(context);

        if (decisionOpt.isEmpty()) {
            return Optional.empty();
        }

        EscalationDecision decision = decisionOpt.get();

        // Idempotency check: verify this level hasn't already been saved
        if (escalationRepository.existsByIncidentIdAndLevel(incident.getId(), decision.level())) {
            return Optional.empty();
        }

        EscalationRecord newRecord = EscalationRecord.create(
                incident.getId(),
                decision.level(),
                decision.targetRole(),
                now,
                decision.reason()
        );

        EscalationRecord saved = escalationRepository.save(newRecord);
        log.info("Escalated incident {} to Level {} (Role: {}) at {}",
                incident.getId(), decision.level(), decision.targetRole(), now);

        // Publish escalation domain event
        IncidentEscalated event = new IncidentEscalated(
                now,
                DomainEvent.SYSTEM_ACTOR,
                incident.getId(),
                incident.getStatus(),
                incident.getStatus(),
                decision.reason(),
                decision.level(),
                decision.targetRole()
        );
        eventPublisher.publish(event);

        return Optional.of(saved);
    }

    /**
     * Scans all open incidents across the repository and triggers any due escalations.
     */
    public List<EscalationRecord> runEscalations() {
        return runEscalations(clock.instant());
    }

    /**
     * Scans all open incidents evaluated at the given instant.
     */
    public List<EscalationRecord> runEscalations(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        List<Incident> allIncidents = incidentRepository.findAll();
        List<EscalationRecord> results = new ArrayList<>();

        for (Incident inc : allIncidents) {
            try {
                evaluateIncident(inc, now).ifPresent(results::add);
            } catch (Exception e) {
                log.warn("Failed to evaluate escalation for incident {}: {}", inc.getId(), e.getMessage());
            }
        }

        return Collections.unmodifiableList(results);
    }

    public List<EscalationRecord> getEscalationsForIncident(String incidentId) {
        return escalationRepository.findByIncidentId(incidentId);
    }

    public Optional<EscalationRecord> getLatestEscalation(String incidentId) {
        return escalationRepository.findLatestByIncidentId(incidentId);
    }

    public List<EscalationRecord> getAllEscalations() {
        return escalationRepository.findAll();
    }
}
