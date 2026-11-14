package com.iitsm.infrastructure.persistence.jpa;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.notification.NotificationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JpaNotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("save, findByRecipientId, and findUnreadByRecipientId round-trips correctly")
    void saveAndQueryNotifications() {
        Instant now = Instant.parse("2026-11-13T10:00:00Z");

        Notification n1 = new Notification(
                "NOTIF-1", "user-1", "Title 1", "Body 1", "INC-1", "IN_APP", false, now
        );
        Notification n2 = new Notification(
                "NOTIF-2", "user-1", "Title 2", "Body 2", "INC-1", "IN_APP", false, now.plusSeconds(10)
        );
        Notification n3 = new Notification(
                "NOTIF-3", "user-2", "Title 3", "Body 3", "INC-2", "IN_APP", false, now.plusSeconds(20)
        );

        notificationRepository.save(n1);
        notificationRepository.save(n2);
        notificationRepository.save(n3);

        entityManager.flush();
        entityManager.clear();

        List<Notification> user1All = notificationRepository.findByRecipientId("user-1");
        assertEquals(2, user1All.size());
        assertEquals("NOTIF-1", user1All.get(0).id());
        assertEquals("NOTIF-2", user1All.get(1).id());

        List<Notification> user1Unread = notificationRepository.findUnreadByRecipientId("user-1");
        assertEquals(2, user1Unread.size());

        // Mark NOTIF-1 as read
        notificationRepository.markRead("NOTIF-1");
        entityManager.flush();
        entityManager.clear();

        List<Notification> user1UnreadAfter = notificationRepository.findUnreadByRecipientId("user-1");
        assertEquals(1, user1UnreadAfter.size());
        assertEquals("NOTIF-2", user1UnreadAfter.get(0).id());
    }

    @Test
    @DisplayName("save with existing ID performs an upsert without duplicating rows")
    void save_withExistingId_performsUpsert() {
        Instant now = Instant.parse("2026-11-13T10:00:00Z");

        Notification original = new Notification(
                "NOTIF-100", "user-3", "Initial Title", "Initial Body", "INC-10", "IN_APP", false, now
        );
        notificationRepository.save(original);
        entityManager.flush();
        entityManager.clear();

        Notification updated = new Notification(
                "NOTIF-100", "user-3", "Updated Title", "Updated Body", "INC-10", "IN_APP", true, now
        );
        notificationRepository.save(updated);
        entityManager.flush();
        entityManager.clear();

        List<Notification> results = notificationRepository.findByRecipientId("user-3");
        assertEquals(1, results.size());
        assertEquals("Updated Title", results.get(0).title());
        assertTrue(results.get(0).read());
    }

    @Test
    @DisplayName("Invalid arguments throw InvalidInputException")
    void invalidArguments_throwException() {
        assertThrows(InvalidInputException.class, () -> notificationRepository.save(null));
        assertThrows(InvalidInputException.class, () -> notificationRepository.findByRecipientId(""));
        assertThrows(InvalidInputException.class, () -> notificationRepository.findByRecipientId(null));
        assertThrows(InvalidInputException.class, () -> notificationRepository.findUnreadByRecipientId(""));
    }
}
