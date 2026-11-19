package com.iitsm.domain.notification;

import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.exception.InvalidInputException;

import java.util.Optional;

/**
 * Maps domain events to notification templates (title + body).
 * Uses string matching on {@link DomainEvent#action()} so future unknown
 * event types are silently ignored.
 */
public final class NotificationFactory {

    /**
     * Maps a DomainEvent to a NotificationTemplate, returning empty if the event type
     * is unknown or should produce no notification.
     */
    public Optional<NotificationTemplate> templateFor(DomainEvent event) {
        if (event == null) {
            throw new InvalidInputException("event must not be null");
        }
        String action = event.action();
        String incidentId = event.incidentId();
        return switch (action) {
            case "INCIDENT_CREATED" -> Optional.of(new NotificationTemplate(
                    "New Incident Reported",
                    "Incident " + incidentId + " has been reported and is awaiting classification."
            ));
            case "INCIDENT_CLASSIFIED" -> Optional.of(new NotificationTemplate(
                    "Incident Classified",
                    "Incident " + incidentId + " has been classified and is awaiting assignment."
            ));
            case "INCIDENT_ASSIGNED" -> Optional.of(new NotificationTemplate(
                    "Incident Assigned",
                    "Incident " + incidentId + " has been assigned to a technician."
            ));
            case "INCIDENT_ACKNOWLEDGED" -> Optional.of(new NotificationTemplate(
                    "Incident Acknowledged",
                    "Incident " + incidentId + " has been acknowledged by the assigned technician."
            ));
            case "INCIDENT_RESOLVED" -> Optional.of(new NotificationTemplate(
                    "Incident Resolved",
                    "Incident " + incidentId + " has been resolved. Please verify the resolution."
            ));
            case "INCIDENT_CLOSED" -> Optional.of(new NotificationTemplate(
                    "Incident Closed",
                    "Incident " + incidentId + " has been closed."
            ));
            case "PRIORITY_CHANGED" -> Optional.of(new NotificationTemplate(
                    "Priority Updated",
                    "The priority of incident " + incidentId + " has been updated."
            ));
            case "ASSIGNMENT_FAILED" -> Optional.of(new NotificationTemplate(
                    "Assignment Failed",
                    "No technician could be assigned to incident " + incidentId + "."
            ));
            case "SLA_WARNING" -> Optional.of(new NotificationTemplate(
                    "SLA Warning",
                    "Incident " + incidentId + " is approaching its SLA deadline."
            ));
            case "SLA_VIOLATED" -> Optional.of(new NotificationTemplate(
                    "SLA Violated",
                    "Incident " + incidentId + " has breached its SLA."
            ));
            case "INCIDENT_ESCALATED" -> Optional.of(new NotificationTemplate(
                    "P1 Incident Escalated",
                    "P1 Incident " + incidentId + " has been escalated due to unacknowledged response delay."
            ));
            case "RESOLUTION_REJECTED" -> Optional.of(new NotificationTemplate(
                    "Resolution Rejected",
                    "The resolution of incident " + incidentId + " was rejected and the incident is back in progress."
            ));
            case "NOTE_ADDED",
                 "ADDITIONAL_INFO_ADDED",
                 "FEEDBACK_SUBMITTED",
                 "INCIDENT_UPDATED",
                 "WORK_STARTED",
                 "INFO_REQUESTED",
                 "INFO_RECEIVED",
                 "VERIFIED",
                 "UNASSIGNED",
                 "INCIDENT_STATUS_CHANGED" -> Optional.of(new NotificationTemplate(
                    "Incident Updated",
                    "Incident " + incidentId + " status has been updated."
            ));
            default -> Optional.empty();
        };
    }
}
