package com.iitsm.api.dto;

/** Body of {@code POST /api/incidents/{id}/feedback}; the comment is optional. */
public record SubmitFeedbackRequest(int rating, String comment) {
}
