package com.iitsm.application.service;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.notification.NotificationRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NotificationQueryServiceTest {

    private InMemoryNotificationRepository realRepository;
    private NotificationQueryService service;

    @BeforeEach
    void setUp() {
        realRepository = new InMemoryNotificationRepository();
        service = new NotificationQueryService(realRepository);
    }

    private static Notification sampleNotification(String id, String recipientId) {
        return new Notification(
                id, recipientId, "Title", "Body",
                "INC-001", "IN_APP", false, Instant.now()
        );
    }

    @Test
    @DisplayName("getNotificationsForUser delegates to repository.findByRecipientId")
    void getNotificationsForUser_delegatesToRepository() {
        Notification n = sampleNotification("n-1", "user-1");
        realRepository.save(n);

        List<Notification> result = service.getNotificationsForUser("user-1");

        assertEquals(1, result.size());
        assertEquals("n-1", result.get(0).id());
    }

    @Test
    @DisplayName("getUnreadNotificationsForUser delegates to repository.findUnreadByRecipientId")
    void getUnreadNotificationsForUser_delegatesToRepository() {
        Notification unread = sampleNotification("n-2", "user-1");
        Notification read = sampleNotification("n-3", "user-1").withRead(true);
        realRepository.save(unread);
        realRepository.save(read);

        List<Notification> result = service.getUnreadNotificationsForUser("user-1");

        assertEquals(1, result.size());
        assertEquals("n-2", result.get(0).id());
    }

    @Test
    @DisplayName("markNotificationRead delegates to repository.markRead")
    void markNotificationRead_delegatesToRepository() {
        Notification n = sampleNotification("notif-1", "user-1");
        realRepository.save(n);

        service.markNotificationRead("notif-1");

        List<Notification> all = realRepository.findAll();
        assertEquals(1, all.size());
        assertTrue(all.get(0).read(), "Notification should be marked as read");
    }

    @Test
    @DisplayName("Blank userId throws InvalidInputException in getNotificationsForUser")
    void blankUserId_throwsInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> service.getNotificationsForUser(""));
        assertThrows(InvalidInputException.class, () -> service.getNotificationsForUser("  "));
        assertThrows(InvalidInputException.class, () -> service.getNotificationsForUser(null));
    }

    @Test
    @DisplayName("Blank userId throws InvalidInputException in getUnreadNotificationsForUser")
    void blankUserId_unread_throwsInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> service.getUnreadNotificationsForUser(""));
        assertThrows(InvalidInputException.class, () -> service.getUnreadNotificationsForUser(null));
    }

    @Test
    @DisplayName("Blank notificationId throws InvalidInputException in markNotificationRead")
    void blankNotificationId_throwsInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> service.markNotificationRead(""));
        assertThrows(InvalidInputException.class, () -> service.markNotificationRead(null));
    }
}
