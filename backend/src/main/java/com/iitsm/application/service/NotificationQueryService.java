package com.iitsm.application.service;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.notification.NotificationRepository;

import java.util.List;

public final class NotificationQueryService {

    private final NotificationRepository repository;

    public NotificationQueryService(NotificationRepository repository) {
        if (repository == null) {
            throw new InvalidInputException("repository must not be null");
        }
        this.repository = repository;
    }

    public List<Notification> getNotificationsForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new InvalidInputException("userId must not be blank");
        }
        return repository.findByRecipientId(userId);
    }

    public List<Notification> getUnreadNotificationsForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new InvalidInputException("userId must not be blank");
        }
        return repository.findUnreadByRecipientId(userId);
    }

    public void markNotificationRead(String notificationId) {
        if (notificationId == null || notificationId.isBlank()) {
            throw new InvalidInputException("notificationId must not be blank");
        }
        repository.markRead(notificationId);
    }
}
