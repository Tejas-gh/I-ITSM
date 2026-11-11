package com.iitsm.application.notification;

import com.iitsm.domain.notification.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SimulatedEmailNotificationChannelTest {

    private static Notification sampleNotification() {
        return new Notification(
                "notif-1", "user-1", "Subject Title", "Email body content",
                "INC-001", "EMAIL", false, Instant.now()
        );
    }

    @Test
    @DisplayName("channelName() returns EMAIL")
    void channelName_returnsEmail() {
        SimulatedEmailNotificationChannel channel = new SimulatedEmailNotificationChannel();
        assertEquals("EMAIL", channel.channelName());
    }

    @Test
    @DisplayName("send() does not throw with a valid notification")
    void send_doesNotThrow() {
        SimulatedEmailNotificationChannel channel = new SimulatedEmailNotificationChannel();
        Notification notification = sampleNotification();

        assertDoesNotThrow(() -> channel.send(notification));
    }
}
