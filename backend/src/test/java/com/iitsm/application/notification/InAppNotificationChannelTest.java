package com.iitsm.application.notification;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.notification.NotificationRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryNotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InAppNotificationChannelTest {

    private static Notification sampleNotification() {
        return new Notification(
                "notif-1", "user-1", "Title", "Body",
                "INC-001", "IN_APP", false, Instant.now()
        );
    }

    @Test
    @DisplayName("channelName() returns IN_APP")
    void channelName_returnsInApp() {
        InAppNotificationChannel channel = new InAppNotificationChannel(new InMemoryNotificationRepository());
        assertEquals("IN_APP", channel.channelName());
    }

    @Test
    @DisplayName("send() calls repository.save() with the notification")
    void send_callsRepositorySave() {
        List<Notification> saved = new ArrayList<>();
        NotificationRepository repo = new NotificationRepository() {
            @Override public void save(Notification notification) { saved.add(notification); }
            @Override public List<Notification> findByRecipientId(String r) { return List.of(); }
            @Override public List<Notification> findUnreadByRecipientId(String r) { return List.of(); }
            @Override public void markRead(String id) {}
            @Override public List<Notification> findAll() { return List.of(); }
        };

        InAppNotificationChannel channel = new InAppNotificationChannel(repo);
        Notification notification = sampleNotification();
        channel.send(notification);

        assertEquals(1, saved.size());
        assertSame(notification, saved.get(0));
    }

    @Test
    @DisplayName("Null repository in constructor throws InvalidInputException")
    void nullRepository_throwsInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> new InAppNotificationChannel(null));
    }
}
