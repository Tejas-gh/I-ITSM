package com.iitsm.config;

import com.iitsm.application.listener.AuditEventListener;
import com.iitsm.application.listener.NotificationEventListener;
import com.iitsm.application.service.NotificationQueryService;
import com.iitsm.domain.event.DomainEventListener;
import com.iitsm.domain.event.DomainEventPublisher;
import com.iitsm.infrastructure.event.InMemoryDomainEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AuditNotificationConfigTest {

    @Autowired
    private DomainEventPublisher publisher;

    @Autowired
    private AuditEventListener auditEventListener;

    @Autowired
    private NotificationEventListener notificationEventListener;

    @Autowired
    private NotificationQueryService notificationQueryService;

    @Test
    @DisplayName("Beans are properly configured in Spring context")
    void beansAreAvailable() {
        assertNotNull(publisher);
        assertNotNull(auditEventListener);
        assertNotNull(notificationEventListener);
        assertNotNull(notificationQueryService);
    }

    @Test
    @DisplayName("AuditEventListener is subscribed FIRST, followed by NotificationEventListener")
    void subscriptionOrderIsAsserted() throws Exception {
        assertTrue(publisher instanceof InMemoryDomainEventPublisher);

        Field listenersField = InMemoryDomainEventPublisher.class.getDeclaredField("listeners");
        listenersField.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<DomainEventListener> listeners = (List<DomainEventListener>) listenersField.get(publisher);

        assertNotNull(listeners);
        assertTrue(listeners.size() >= 2);

        int auditIndex = -1;
        int notifIndex = -1;

        for (int i = 0; i < listeners.size(); i++) {
            DomainEventListener l = listeners.get(i);
            if (l instanceof AuditEventListener) {
                auditIndex = i;
            } else if (l instanceof NotificationEventListener) {
                notifIndex = i;
            }
        }

        assertTrue(auditIndex >= 0, "AuditEventListener must be subscribed");
        assertTrue(notifIndex >= 0, "NotificationEventListener must be subscribed");
        assertTrue(auditIndex < notifIndex, "AuditEventListener must subscribe BEFORE NotificationEventListener");
    }
}
