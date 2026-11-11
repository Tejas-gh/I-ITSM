package com.iitsm.application.notification;

import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.IncidentAssigned;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.User;
import com.iitsm.domain.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves which user IDs should receive a notification for a given domain event (D2 matrix).
 * Always excludes the actor themselves and inactive users.
 */
public final class NotificationRecipientResolver {

    private final UserRepository userRepository;

    public NotificationRecipientResolver(UserRepository userRepository) {
        if (userRepository == null) {
            throw new InvalidInputException("userRepository must not be null");
        }
        this.userRepository = userRepository;
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
                 "INCIDENT_ACKNOWLEDGED",
                 "INCIDENT_RESOLVED",
                 "PRIORITY_CHANGED",
                 "ASSIGNMENT_FAILED",
                 // IncidentUpdated / IncidentStatusChanged actions
                 "NOTE_ADDED",
                 "ADDITIONAL_INFO_ADDED",
                 "FEEDBACK_SUBMITTED",
                 "INCIDENT_UPDATED",
                 "WORK_STARTED",
                 "INFO_REQUESTED",
                 "INFO_RECEIVED",
                 "RESOLUTION_REJECTED",
                 "VERIFIED",
                 "UNASSIGNED",
                 "INCIDENT_STATUS_CHANGED" -> filterAndCollect(
                    allUsers, actorId,
                    u -> isAdmin(u)
            );
            case "INCIDENT_ASSIGNED" -> {
                // Notify assigned technician + all admins
                List<String> recipients = new ArrayList<>();
                // Cast safely after action check
                if (event instanceof IncidentAssigned assigned) {
                    String technicianId = assigned.technicianId();
                    // Add technician if active and not actor
                    for (User u : allUsers) {
                        if (u.getId().equals(technicianId)
                                && u.isActive()
                                && !u.getId().equals(actorId)) {
                            recipients.add(u.getId());
                            break;
                        }
                    }
                }
                // Add admins
                for (User u : allUsers) {
                    if (isAdmin(u) && u.isActive() && !u.getId().equals(actorId)
                            && !recipients.contains(u.getId())) {
                        recipients.add(u.getId());
                    }
                }
                yield recipients;
            }
            case "INCIDENT_CLOSED" -> filterAndCollect(
                    allUsers, actorId,
                    u -> true  // all active users
            );
            case "SLA_WARNING", "SLA_VIOLATED" -> filterAndCollect(
                    allUsers, actorId,
                    u -> isAdmin(u) || isTechnician(u)
            );
            default -> List.of();
        };
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

    private static boolean isAdmin(User u) {
        return u.getRole() == Role.ADMINISTRATOR || u.getRole() == Role.IT_MANAGER;
    }

    private static boolean isTechnician(User u) {
        return u.getRole() == Role.TECHNICIAN || u.getRole() == Role.TEAM_LEAD;
    }
}
