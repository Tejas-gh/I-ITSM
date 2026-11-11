package com.iitsm.domain.notification;

public interface NotificationChannel {
    String channelName();
    void send(Notification notification);
}
