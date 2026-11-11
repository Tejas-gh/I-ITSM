package com.iitsm.application.notification;

import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.notification.NotificationChannel;

public final class SimulatedEmailNotificationChannel implements NotificationChannel {

    private static final System.Logger LOGGER =
            System.getLogger(SimulatedEmailNotificationChannel.class.getName());

    @Override
    public String channelName() {
        return "EMAIL";
    }

    @Override
    public void send(Notification notification) {
        LOGGER.log(System.Logger.Level.WARNING,
                "SIMULATED EMAIL to recipientId=" + notification.recipientId()
                        + " subject=" + notification.title());
    }
}
