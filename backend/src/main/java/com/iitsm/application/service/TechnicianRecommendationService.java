package com.iitsm.application.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.iitsm.domain.assignment.AssignmentStrategy;
import com.iitsm.domain.assignment.TechnicianScore;
import com.iitsm.domain.exception.IncidentNotFoundException;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.exception.UserNotFoundException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.repository.UserRepository;

/**
 * Intelligent recommendation service that inspects available technicians, considers the active
 * assignment strategy, and produces ranked candidate recommendations for an incident.
 *
 * <p>Operates separately from auto-assignment, allowing authorized administrators and team leads
 * to review ranked recommendations and manually select the best technician.</p>
 */
public final class TechnicianRecommendationService {

    private final IncidentRepository incidents;
    private final UserRepository users;
    private final AssignmentStrategy assignmentStrategy;
    private final IncidentPermissionPolicy permissions;

    public TechnicianRecommendationService(IncidentRepository incidents,
                                           UserRepository users,
                                           AssignmentStrategy assignmentStrategy,
                                           IncidentPermissionPolicy permissions) {
        this.incidents = Objects.requireNonNull(incidents, "incidents must not be null");
        this.users = Objects.requireNonNull(users, "users must not be null");
        this.assignmentStrategy = Objects.requireNonNull(assignmentStrategy, "assignmentStrategy must not be null");
        this.permissions = Objects.requireNonNull(permissions, "permissions must not be null");
    }

    /**
     * Produces ranked technician recommendations for the specified incident.
     *
     * @param actorId the ID of the user requesting recommendations (must have ASSIGN permission)
     * @param incidentId the ID of the incident
     * @return a list of ranked recommendations ordered from best to worst fit
     */
    public List<TechnicianRecommendation> getRecommendations(String actorId, String incidentId) {
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be blank");
        }

        User actor = loadActiveUser(actorId);
        Incident incident = loadIncident(incidentId);

        permissions.check(actor, IncidentOperation.ASSIGN, incident);

        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new InvalidInputException("Cannot recommend technicians for a closed incident: " + incidentId);
        }

        List<Technician> allTechnicians = users.findAllTechnicians();
        Map<String, Integer> workload = new HashMap<>();
        List<Technician> candidates = new ArrayList<>();

        for (Technician tech : allTechnicians) {
            int active = incidents.countActiveByTechnicianId(tech.getId());
            workload.put(tech.getId(), active);
            if (tech.isAssignable() && !tech.isOverloaded(active)) {
                candidates.add(tech);
            }
        }

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<TechnicianScore> ranked = assignmentStrategy.rank(incident, candidates, workload);

        List<TechnicianRecommendation> recommendations = new ArrayList<>(ranked.size());
        for (TechnicianScore ts : ranked) {
            Technician tech = ts.technician();
            int active = workload.getOrDefault(tech.getId(), 0);
            recommendations.add(new TechnicianRecommendation(
                    tech.getId(),
                    tech.getName(),
                    tech.getEmail(),
                    tech.getRole(),
                    ts.score(),
                    ts.reason(),
                    active,
                    tech.getMaxActiveIncidents(),
                    tech.getSkillLevels(),
                    tech.getYearsOfExperience()
            ));
        }

        return Collections.unmodifiableList(recommendations);
    }

    private User loadActiveUser(String userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        if (!user.isActive()) {
            throw new UnauthorizedOperationException("User is inactive: " + userId);
        }
        return user;
    }

    private Incident loadIncident(String incidentId) {
        return incidents.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException("Incident not found: " + incidentId));
    }
}
