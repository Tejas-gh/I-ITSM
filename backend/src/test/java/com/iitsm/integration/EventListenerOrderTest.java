package com.iitsm.integration;

import com.iitsm.application.listener.AuditEventListener;
import com.iitsm.application.listener.NotificationEventListener;
import com.iitsm.application.notification.NotificationRecipientResolver;
import com.iitsm.config.AuditNotificationConfig;
import com.iitsm.domain.audit.AuditEntry;
import com.iitsm.domain.audit.AuditRepository;
import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.DomainEventListener;
import com.iitsm.domain.event.DomainEventPublisher;
import com.iitsm.domain.event.IncidentCreated;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.notification.NotificationChannel;
import com.iitsm.domain.notification.NotificationFactory;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.infrastructure.event.InMemoryDomainEventPublisher;
import com.iitsm.infrastructure.persistence.memory.InMemoryAuditRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate B7: Verifies that AuditEventListener strictly executes before NotificationEventListener.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:b7-event-order;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@DirtiesContext
@Transactional
public class EventListenerOrderTest {

    @Autowired
    private DomainEventPublisher publisher;

    @Autowired
    private AuditEventListener auditEventListener;

    @Autowired
    private NotificationEventListener notificationEventListener;

    @Autowired
    private AuditRepository auditRepository;

    @Test
    void publisherListeners_orderedAuditBeforeNotification() throws Exception {
        // Inspect the internal listener list of InMemoryDomainEventPublisher
        Field listenersField = InMemoryDomainEventPublisher.class.getDeclaredField("listeners");
        listenersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        CopyOnWriteArrayList<DomainEventListener> list =
                (CopyOnWriteArrayList<DomainEventListener>) listenersField.get(publisher);

        int auditIndex = -1;
        int notifIndex = -1;

        for (int i = 0; i < list.size(); i++) {
            DomainEventListener l = list.get(i);
            if (l instanceof AuditEventListener) {
                auditIndex = i;
            } else if (l instanceof NotificationEventListener) {
                notifIndex = i;
            }
        }

        assertTrue(auditIndex >= 0, "AuditEventListener must be subscribed to publisher");
        assertTrue(notifIndex >= 0, "NotificationEventListener must be subscribed to publisher");
        assertTrue(auditIndex < notifIndex,
                "AuditEventListener (index " + auditIndex + ") must execute BEFORE NotificationEventListener (index " + notifIndex + ")");
    }

    @Test
    void eventDelivery_auditRecordExistsWhenNotificationRuns() {
        InMemoryDomainEventPublisher isolatedPublisher = new InMemoryDomainEventPublisher();
        AuditRepository testAuditRepo = new InMemoryAuditRepository();
        UserRepository testUserRepo = new InMemoryUserRepository();
        List<String> executionOrder = new ArrayList<>();

        AuditEventListener realAuditListener = new AuditEventListener(testAuditRepo);
        DomainEventListener testAuditListener = new DomainEventListener() {
            @Override
            public void onEvent(DomainEvent event) {
                executionOrder.add("AUDIT_START");
                realAuditListener.onEvent(event);
                executionOrder.add("AUDIT_END");
            }

            @Override
            public boolean critical() {
                return realAuditListener.critical();
            }
        };

        NotificationChannel orderCheckingChannel = new NotificationChannel() {
            @Override
            public String channelName() {
                return "ORDER_CHECK";
            }

            @Override
            public void send(com.iitsm.domain.notification.Notification notification) {
                executionOrder.add("NOTIFICATION_SEND");
                // Verify that the audit record is ALREADY persisted in the repository!
                List<AuditEntry> entries = testAuditRepo.findByIncidentId(notification.incidentId());
                assertFalse(entries.isEmpty(),
                        "Audit entry must already exist when notification channel sends");
            }
        };

        NotificationEventListener testNotifListener = new NotificationEventListener(
                new NotificationFactory(),
                new NotificationRecipientResolver(testUserRepo),
                List.of(orderCheckingChannel)
        );

        // Audit first, then notification (as enforced in AuditNotificationConfig)
        isolatedPublisher.subscribe(testAuditListener);
        isolatedPublisher.subscribe(testNotifListener);

        DomainEvent event = new IncidentCreated(
                Instant.now(),
                "EMP01",
                "INC_ORDER_1",
                null,
                IncidentStatus.NEW,
                "Create incident",
                "EMP01",
                "SVC1",
                "CAT1",
                null,
                com.iitsm.domain.model.CategorySource.MANUAL,
                PriorityLevel.P2,
                24
        );

        isolatedPublisher.publish(event);

        assertEquals(List.of("AUDIT_START", "AUDIT_END"), executionOrder.subList(0, 2),
                "AuditEventListener must start and finish before NotificationEventListener runs");
    }
}
