package com.iitsm.application.service;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.exception.UserNotFoundException;
import com.iitsm.domain.escalation.EscalationRepository;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.User;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.repository.IncidentSearchCriteria;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.domain.sla.SlaEvaluation;
import com.iitsm.domain.sla.SlaEvaluator;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Application service providing role-scoped incident searching with multi-criteria filters and SLA status evaluation.
 */
public final class IncidentSearchService {

    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final SlaEvaluator slaEvaluator;
    private final Clock clock;
    private final EscalationRepository escalationRepository;

    public IncidentSearchService(IncidentRepository incidentRepository,
                                 UserRepository userRepository,
                                 SlaEvaluator slaEvaluator,
                                 Clock clock) {
        this(incidentRepository, userRepository, slaEvaluator, clock, null);
    }

    public IncidentSearchService(IncidentRepository incidentRepository,
                                 UserRepository userRepository,
                                 SlaEvaluator slaEvaluator,
                                 Clock clock,
                                 EscalationRepository escalationRepository) {
        if (incidentRepository == null) {
            throw new InvalidInputException("incidentRepository must not be null");
        }
        if (userRepository == null) {
            throw new InvalidInputException("userRepository must not be null");
        }
        if (slaEvaluator == null) {
            throw new InvalidInputException("slaEvaluator must not be null");
        }
        if (clock == null) {
            throw new InvalidInputException("clock must not be null");
        }
        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.slaEvaluator = slaEvaluator;
        this.clock = clock;
        this.escalationRepository = escalationRepository;
    }

    /**
     * Searches incidents visible to the actor based on their role, criteria, and SLA status.
     *
     * @param actorId the identifier of the actor executing the search
     * @param query   the search query (null is treated as empty query)
     * @return unmodifiable list of matching incidents in repository insertion order
     */
    public List<Incident> search(String actorId, IncidentSearchQuery query) {
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + actorId));

        if (!actor.isActive()) {
            throw new UnauthorizedOperationException("User is inactive: " + actorId);
        }

        if (query == null) {
            query = IncidentSearchQuery.empty();
        }

        Role role = actor.getRole();
        String effectiveTechId = query.technicianId();

        if (role == Role.TECHNICIAN || role == Role.TEAM_LEAD) {
            // A technician passing another technician's id gets an empty result.
            if (query.technicianId() != null && !query.technicianId().equals(actorId)) {
                return List.of();
            }
            // With no explicit technician filter, fetch all query-matching incidents so
            // scoped escalations can be added to the actor's normally assigned incidents.
            effectiveTechId = query.technicianId();
        }

        IncidentSearchCriteria criteria = new IncidentSearchCriteria(
                query.incidentId(),
                query.createdFrom(),
                query.createdTo(),
                query.status(),
                query.priority(),
                query.categoryId(),
                query.serviceId(),
                effectiveTechId
        );

        List<Incident> candidates = incidentRepository.search(criteria);

        // Read clock once per search
        Instant now = clock.instant();

        List<Incident> result = new ArrayList<>();
        for (Incident inc : candidates) {
            // TEAM_LEAD/TECHNICIAN normally see assigned incidents, plus incidents
            // whose latest persisted escalation targets their current role.
            if ((role == Role.TECHNICIAN || role == Role.TEAM_LEAD)
                    && query.technicianId() == null
                    && !isAssignedToActor(inc, actorId)
                    && !isEscalatedToRole(inc, role)) {
                continue;
            }

            // Role visibility filter for Employee (only reported incidents)
            if (role == Role.EMPLOYEE && !inc.getReporter().getId().equals(actorId)) {
                continue;
            }

            // SLA status filter
            if (query.slaStatus() != null) {
                // If incident has no deadlines, exclude safely without throwing exception
                if (inc.getResponseDeadline().isEmpty() || inc.getResolutionDeadline().isEmpty()) {
                    continue;
                }
                SlaEvaluation evaluation = slaEvaluator.evaluate(inc, now);
                if (evaluation.overall() != query.slaStatus()) {
                    continue;
                }
            }

            result.add(inc);
        }

        return Collections.unmodifiableList(result);
    }

    private boolean isAssignedToActor(Incident incident, String actorId) {
        return incident.getAssignedTechnician()
                .map(technician -> technician.getId().equals(actorId))
                .orElse(false);
    }

    private boolean isEscalatedToRole(Incident incident, Role role) {
        if (escalationRepository == null || incident.getStatus() == IncidentStatus.CLOSED) {
            return false;
        }
        return escalationRepository.findLatestByIncidentId(incident.getId())
                .map(record -> record.targetRole() == role)
                .orElse(false);
    }
}
