package com.iitsm.domain.incident;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.NoteType;

import java.time.Instant;

/**
 * An immutable note attached to an incident (investigation, info request/response, additional info).
 */
public record IncidentNote(String authorId, NoteType type, String text, Instant createdAt) {

    public IncidentNote {
        if (authorId == null || authorId.trim().isEmpty()) {
            throw new InvalidInputException("authorId must not be blank");
        }
        authorId = authorId.trim();
        if (type == null) {
            throw new InvalidInputException("type must not be null");
        }
        if (text == null || text.trim().isEmpty()) {
            throw new InvalidInputException("text must not be blank");
        }
        text = text.trim();
        if (createdAt == null) {
            throw new InvalidInputException("createdAt must not be null");
        }
    }
}

