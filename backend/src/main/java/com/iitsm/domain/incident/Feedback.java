package com.iitsm.domain.incident;

import com.iitsm.domain.exception.InvalidInputException;

import java.time.Instant;

/**
 * Immutable reporter feedback on a resolved incident.
 * Rating must be 1–5; comment is optional (null normalised to empty string).
 */
public record Feedback(String authorId, int rating, String comment, Instant submittedAt) {

    public Feedback {
        if (authorId == null || authorId.trim().isEmpty()) {
            throw new InvalidInputException("authorId must not be blank");
        }
        authorId = authorId.trim();
        if (rating < 1 || rating > 5) {
            throw new InvalidInputException("rating must be between 1 and 5");
        }
        if (comment == null) {
            comment = "";
        }
        if (submittedAt == null) {
            throw new InvalidInputException("submittedAt must not be null");
        }
    }
}

