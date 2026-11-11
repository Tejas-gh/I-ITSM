package com.iitsm.domain.notification;

import com.iitsm.domain.exception.InvalidInputException;

import java.time.Instant;

public record Notification(
        String id,
        String recipientId,
        String title,
        String body,
        String incidentId,
        String channelName,
        boolean read,
        Instant createdAt
) {

    public Notification {
        if (id == null || id.isBlank()) {
            throw new InvalidInputException("id must not be blank");
        }
        if (recipientId == null || recipientId.isBlank()) {
            throw new InvalidInputException("recipientId must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new InvalidInputException("title must not be blank");
        }
        if (body == null || body.isBlank()) {
            throw new InvalidInputException("body must not be blank");
        }
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be blank");
        }
        if (channelName == null || channelName.isBlank()) {
            throw new InvalidInputException("channelName must not be blank");
        }
        if (createdAt == null) {
            throw new InvalidInputException("createdAt must not be null");
        }
    }

    /** Convenience method delegating to the record accessor {@link #read()}. */
    public boolean isRead() {
        return read();
    }

    /** Returns a new Notification with the given read state. */
    public Notification withRead(boolean read) {
        return new Notification(id, recipientId, title, body, incidentId, channelName, read, createdAt);
    }
}
