package com.iitsm.api.dto;

import com.iitsm.domain.notification.Notification;

import java.time.Instant;

/**
 * Representation of a notification message returned over HTTP.
 */
public record NotificationResponse(
        String id,
        String recipientId,
        String title,
        String body,
        String incidentId,
        String channelName,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.id(),
                n.recipientId(),
                n.title(),
                n.body(),
                n.incidentId(),
                n.channelName(),
                n.read(),
                n.createdAt()
        );
    }
}
