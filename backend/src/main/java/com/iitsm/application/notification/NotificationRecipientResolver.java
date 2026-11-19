package com.iitsm.application.notification;

import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.IncidentAcknowledged;
import com.iitsm.domain.event.IncidentAssigned;
import com.iitsm.domain.event.IncidentEscalated;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.User;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.repository.UserRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves which user IDs should receive a notification for a given domain event (D2 matrix).
 * ASSIGNED/ACKNOWLEDGED/INFO_REQUESTED/RESOLVED/CLOSED go to assignee + admins + reporter, de-duplicated.
 * Always excludes the actor themselves and inactive users.
 */
public final class NotificationRecipientResolver {

    private final UserRepository userRepository;
    private final IncidentRepository incidentRepository; // nullable: legacy constructor

    /** Legacy constructor: incident-aware rules fall back to the event data and admins only. */
    public NotificationRecipientResolver(UserRepository userRepository) {
        this(userRepository, null);
    }

    public NotificationRecipientResolver(UserRepository userRepository, IncidentRepository incidentRepository) {
        if (userRepository == null) {
            throw new InvalidInputException("userRepository must not be null");
        }
        this.userRepository = userRepository;
        this.incidentRepository = incidentRepository;
    }

    /**
     * Returns recipient user IDs for the given event, excluding the actor and inactive users.
     */
    public List<String> resolveRecipientIds(DomainEvent event) {
        if (event == null) {
            throw new InvalidInputException("event must not be null");
        }

        String action = event.action();
        String actorId = event.actorId();
        List<User> allUsers = userRepository.findAll();

        return switch (action) {
            case "INCIDENT_CREATED",
                 "INCIDENT_CLASSIFIED",
                 "PRIORITY_CHANGED",
                 "ASSIGNMENT_FAILED",
                 // IncidentUpdated / IncidentStatusChanged actions
                 "NOTE_ADDED",
                 "ADDITIONAL_INFO_ADDED",
                 "FEEDBACK_SUBMITTED",
                 "INCIDENT_UPDATED",
                 "WORK_STARTED",
                 "INFO_RECEIVED",
                 "VERIFIED",
                 "UNASSIGNED",
                 "INCIDENT_STATUS_CHANGED" -> filterAndCollect(
                    allUsers, actorId,
                    u -> isAdmin(u)
            );
            // Lifecycle events: assignee + admins + reporter (no broadcast to unrelated users)
            case "INCIDENT_ASSIGNED",
                 "INCIDENT_ACKNOWLEDGED",
                 "INFO_REQUESTED",
                 "RESOLUTION_REJECTED",
                 "INCIDENT_RESOLVED",
                 "INCIDENT_CLOSED" -> resolveLifecycleRecipients(event, allUsers);
            case "SLA_WARNING", "SLA_VIOLATED" -> filterAndCollect(
                    allUsers, actorId,
                    u -> isAdmin(u) || isTechnician(u)
            );
            case "INCIDENT_ESCALATED" -> resolveEscalationRecipients(event, allUsers);
            default -> List.of();
        };
    }

    private List<String> resolveLifecycleRecipients(DomainEvent event, List<User> allUsers) {
        Optional<Incident> incident = incidentRepository == null
                ? Optional.empty()
                : incidentRepository.findById(event.incidentId());

        Set<String> candidateIds = new LinkedHashSet<>(); // de-duplicates overlapping groups
        // Assignee: event data is authoritative for assignment/acknowledgement, else the incident
        String eventTechnician = null;
        if (event instanceof IncidentAssigned assigned) {
            eventTechnician = assigned.technicianId();
        } else if (event instanceof IncidentAcknowledged ack) {
            eventTechnician = ack.technicianId();
        }
        if (eventTechnician != null) {
            candidateIds.add(eventTechnician);
        } else {
            incident.flatMap(Incident::getAssignedTechnician).ifPresent(t -> candidateIds.add(t.getId()));
        }
        incident.ifPresent(i -> candidateIds.add(i.getReporter().getId()));
        for (User u : allUsers) {
            if (isAdmin(u)) {
                candidateIds.add(u.getId());
            }
        }

        List<String> result = new ArrayList<>();
        for (User u : allUsers) { // keep only existing, active users; exclude the actor
            if (candidateIds.contains(u.getId()) && u.isActive() && !u.getId().equals(event.actorId())) {
                result.add(u.getId());
            }
        }
        return result;
    }

    private List<String> filterAndCollect(List<User> allUsers, String actorId,
                                           java.util.function.Predicate<User> rolePredicate) {
        List<String> result = new ArrayList<>();
        for (User u : allUsers) {
            if (rolePredicate.test(u) && u.isActive() && !u.getId().equals(actorId)) {
                result.add(u.getId());
            }
        }
        return result;
    }

    private List<String> resolveEscalationRecipients(DomainEvent event, List<User> allUsers) {
        Role targetRole = null;
        if (event instanceof IncidentEscalated escalated) {
            targetRole = escalated.targetRole();
        }
        final Role roleToNotify = targetRole;

        return filterAndCollect(
                allUsers, event.actorId(),
                u -> isAdmin(u) || (roleToNotify != null && u.getRole() == roleToNotify)
        );
    }

    private static boolean isAdmin(User u) {
        return u.getRole() == Role.ADMINISTRATOR || u.getRole() == Role.IT_MANAGER;
    }

    private static boolean isTechnician(User u) {
        return u.getRole() == Role.TECHNICIAN || u.getRole() == Role.TEAM_LEAD;
    }
}
