package com.iitsm.domain.notification;

import java.util.List;

public interface NotificationRepository {
    void save(Notification notification);
    List<Notification> findByRecipientId(String recipientId);
    List<Notification> findUnreadByRecipientId(String recipientId);
    void markRead(String notificationId);
    List<Notification> findAll();
}
