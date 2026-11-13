package com.iitsm.api.dto;

import java.util.List;

import com.iitsm.domain.classification.CategorySuggestion;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Technician;

/** Domain to response mapping. Plain Java: no framework types. */
public final class IncidentResponseMapper {

    private IncidentResponseMapper() {
    }

    public static IncidentResponse toResponse(Incident i) {
        List<IncidentResponse.NoteResponse> notes = i.getNotes().stream()
                .map(n -> new IncidentResponse.NoteResponse(n.authorId(), n.type(), n.text(), n.createdAt()))
                .toList();
        IncidentResponse.ResolutionResponse resolution = i.getResolution()
                .map(r -> new IncidentResponse.ResolutionResponse(r.resolverId(), r.summary(), r.resolvedAt()))
                .orElse(null);
        IncidentResponse.FeedbackResponse feedback = i.getFeedback()
                .map(f -> new IncidentResponse.FeedbackResponse(f.authorId(), f.rating(), f.comment(), f.submittedAt()))
                .orElse(null);
        return new IncidentResponse(
                i.getId(),
                i.getTitle(),
                i.getDescription(),
                i.getReporter().getId(),
                i.getService().getId(),
                i.getCategory().getId(),
                i.getSuggestedCategory().map(Category::getId).orElse(null),
                i.getCategorySource(),
                i.getImpact(),
                i.getUrgency(),
                i.getAffectedUsers(),
                i.getPriority(),
                i.getPriorityScore(),
                i.getStatus(),
                i.getAssignedTechnician().map(Technician::getId).orElse(null),
                i.getCreatedAt(),
                i.getResponseDeadline().orElse(null),
                i.getResolutionDeadline().orElse(null),
                i.getAcknowledgedAt().orElse(null),
                i.getResolvedAt().orElse(null),
                i.getVerifiedAt().orElse(null),
                i.getClosedAt().orElse(null),
                notes,
                resolution,
                feedback);
    }

    public static List<IncidentResponse> toResponses(List<Incident> incidents) {
        return incidents.stream().map(IncidentResponseMapper::toResponse).toList();
    }

    public static CategorySuggestionResponse toResponse(CategorySuggestion s) {
        return new CategorySuggestionResponse(s.categoryId(), s.confidence(), s.reason(), s.source());
    }
}
