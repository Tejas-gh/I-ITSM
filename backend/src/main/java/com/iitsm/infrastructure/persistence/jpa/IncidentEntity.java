package com.iitsm.infrastructure.persistence.jpa;

import java.time.Instant;

import com.iitsm.domain.incident.Feedback;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.Resolution;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Urgency;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA structure for {@code incidents}. Relations are held as plain ids; the adapter resolves them to
 * domain objects. Resolution and feedback are inline column groups (A3 schema).
 */
@Entity
@Table(name = "incidents")
public class IncidentEntity {

    @Id
    String id;
    String title;
    String description;
    String reporterId;
    String serviceId;
    String categoryId;
    String suggestedCategoryId;
    @Enumerated(EnumType.STRING)
    CategorySource categorySource;
    @Enumerated(EnumType.STRING)
    Impact impact;
    @Enumerated(EnumType.STRING)
    Urgency urgency;
    int affectedUsers;
    @Enumerated(EnumType.STRING)
    PriorityLevel priority;
    int priorityScore;
    Instant createdAt;
    @Enumerated(EnumType.STRING)
    IncidentStatus status;
    String assignedTechnicianId;
    Instant assignedAt;
    Instant acknowledgedAt;
    Instant verifiedAt;
    Instant closedAt;
    String closedById;
    String closureNote;
    Instant responseDeadline;
    Instant resolutionDeadline;
    Instant waitingSince;
    long pausedDurationNanos;
    String resolutionResolverId;
    String resolutionSummary;
    Instant resolutionResolvedAt;
    String feedbackAuthorId;
    Integer feedbackRating;
    String feedbackComment;
    Instant feedbackSubmittedAt;

    protected IncidentEntity() {
    }

    static IncidentEntity from(Incident incident) {
        IncidentEntity e = new IncidentEntity();
        e.id = incident.getId();
        e.title = incident.getTitle();
        e.description = incident.getDescription();
        e.reporterId = incident.getReporter().getId();
        e.serviceId = incident.getService().getId();
        e.categoryId = incident.getCategory().getId();
        e.suggestedCategoryId = incident.getSuggestedCategory().map(c -> c.getId()).orElse(null);
        e.categorySource = incident.getCategorySource();
        e.impact = incident.getImpact();
        e.urgency = incident.getUrgency();
        e.affectedUsers = incident.getAffectedUsers();
        e.priority = incident.getPriority();
        e.priorityScore = incident.getPriorityScore();
        e.createdAt = incident.getCreatedAt();
        e.status = incident.getStatus();
        e.assignedTechnicianId = incident.getAssignedTechnician().map(t -> t.getId()).orElse(null);
        e.assignedAt = incident.getAssignedAt().orElse(null);
        e.acknowledgedAt = incident.getAcknowledgedAt().orElse(null);
        e.verifiedAt = incident.getVerifiedAt().orElse(null);
        e.closedAt = incident.getClosedAt().orElse(null);
        e.closedById = incident.getClosedById().orElse(null);
        e.closureNote = incident.getClosureNote().orElse(null);
        e.responseDeadline = incident.getResponseDeadline().orElse(null);
        e.resolutionDeadline = incident.getResolutionDeadline().orElse(null);
        e.waitingSince = incident.getWaitingSince().orElse(null);
        e.pausedDurationNanos = incident.getPausedDuration().toNanos();
        Resolution resolution = incident.getResolution().orElse(null);
        if (resolution != null) {
            e.resolutionResolverId = resolution.resolverId();
            e.resolutionSummary = resolution.summary();
            e.resolutionResolvedAt = resolution.resolvedAt();
        }
        Feedback feedback = incident.getFeedback().orElse(null);
        if (feedback != null) {
            e.feedbackAuthorId = feedback.authorId();
            e.feedbackRating = feedback.rating();
            e.feedbackComment = feedback.comment();
            e.feedbackSubmittedAt = feedback.submittedAt();
        }
        return e;
    }
}
