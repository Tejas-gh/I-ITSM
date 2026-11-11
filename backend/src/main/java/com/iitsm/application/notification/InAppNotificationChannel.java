package com.iitsm.application.notification;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.notification.NotificationChannel;
import com.iitsm.domain.notification.NotificationRepository;

public final class InAppNotificationChannel implements NotificationChannel {

    private final NotificationRepository repository;

    public InAppNotificationChannel(NotificationRepository repository) {
        if (repository == null) {
            throw new InvalidInputException("repository must not be null");
        }
        this.repository = repository;
    }

    @Override
    public String channelName() {
        return "IN_APP";
    }

    @Override
    public void send(Notification notification) {
        repository.save(notification);
    }
}
