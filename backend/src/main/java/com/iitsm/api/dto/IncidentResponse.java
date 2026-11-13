package com.iitsm.api.dto;

import java.time.Instant;
import java.util.List;

import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.NoteType;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Urgency;

/** Incident representation; the field list is exactly the one in CONTRACT_FREEZE.md §U.1. Absent values are null. */
public record IncidentResponse(
        String id,
        String title,
        String description,
        String reporterId,
        String serviceId,
        String categoryId,
        String suggestedCategoryId,
        CategorySource categorySource,
        Impact impact,
        Urgency urgency,
        int affectedUsers,
        PriorityLevel priority,
        int priorityScore,
        IncidentStatus status,
        String assignedTechnicianId,
        Instant createdAt,
        Instant responseDeadline,
        Instant resolutionDeadline,
        Instant acknowledgedAt,
        Instant resolvedAt,
        Instant verifiedAt,
        Instant closedAt,
        List<NoteResponse> notes,
        ResolutionResponse resolution,
        FeedbackResponse feedback) {

    public record NoteResponse(String authorId, NoteType type, String text, Instant createdAt) {
    }

    public record ResolutionResponse(String resolverId, String summary, Instant resolvedAt) {
    }

    public record FeedbackResponse(String authorId, int rating, String comment, Instant submittedAt) {
    }
}
