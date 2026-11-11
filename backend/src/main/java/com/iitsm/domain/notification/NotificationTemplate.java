package com.iitsm.domain.notification;

/** Holds the title and body for a notification derived from a domain event. */
public record NotificationTemplate(String title, String body) {
}
