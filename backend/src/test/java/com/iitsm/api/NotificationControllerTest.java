package com.iitsm.api;

import com.iitsm.api.dto.NotificationResponse;
import com.iitsm.application.service.NotificationQueryService;
import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.notification.NotificationRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationControllerTest {

    private NotificationRepository repository;
    private NotificationController controller;

    private static Authentication authFor(String userId) {
        return new TestingAuthenticationToken(userId, null);
    }

    private final Authentication userAuth = authFor("TECH01");

    @BeforeEach
    void setUp() {
        repository = new InMemoryNotificationRepository();
        NotificationQueryService queryService = new NotificationQueryService(repository);
        controller = new NotificationController(queryService);
    }

    @Test
    void getNotifications_returnsUserNotifications() {
        Notification n1 = new Notification(
                "N1", "TECH01", "Assigned", "You have been assigned", "INC1001", "IN_APP", false, Instant.now()
        );
        Notification n2 = new Notification(
                "N2", "EMP01", "Created", "Incident created", "INC1001", "IN_APP", false, Instant.now()
        );
        repository.save(n1);
        repository.save(n2);

        List<NotificationResponse> list = controller.getNotifications(userAuth);

        assertEquals(1, list.size());
        assertEquals("N1", list.get(0).id());
        assertEquals("TECH01", list.get(0).recipientId());
    }

    @Test
    void getUnreadNotifications_returnsOnlyUnread() {
        Notification unread = new Notification(
                "N1", "TECH01", "Title 1", "Body 1", "INC1001", "IN_APP", false, Instant.now()
        );
        Notification read = new Notification(
                "N2", "TECH01", "Title 2", "Body 2", "INC1001", "IN_APP", true, Instant.now()
        );
        repository.save(unread);
        repository.save(read);

        List<NotificationResponse> list = controller.getUnreadNotifications(userAuth);

        assertEquals(1, list.size());
        assertEquals("N1", list.get(0).id());
        assertFalse(list.get(0).read());
    }

    @Test
    void markAsRead_updatesNotificationStatus() {
        Notification unread = new Notification(
                "N1", "TECH01", "Title 1", "Body 1", "INC1001", "IN_APP", false, Instant.now()
        );
        repository.save(unread);

        ResponseEntity<Void> response = controller.markAsRead("N1");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertTrue(repository.findAll().get(0).isRead());
    }
}
