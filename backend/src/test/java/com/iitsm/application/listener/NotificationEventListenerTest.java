package com.iitsm.application.listener;

import com.iitsm.application.notification.NotificationRecipientResolver;
import com.iitsm.domain.event.*;
import com.iitsm.domain.model.*;
import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.notification.NotificationChannel;
import com.iitsm.domain.notification.NotificationFactory;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NotificationEventListenerTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    private NotificationFactory factory;
    private InMemoryUserRepository userRepository;
    private NotificationRecipientResolver resolver;
    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        factory = new NotificationFactory();
        userRepository = new InMemoryUserRepository();
        resolver = new NotificationRecipientResolver(userRepository);
    }

    @Test
    @DisplayName("critical() returns false")
    void criticalReturnsFalse() {
        listener = new NotificationEventListener(factory, resolver, List.of());
        assertFalse(listener.critical());
    }

    @Test
    @DisplayName("Unknown event type is silently ignored — no exception thrown")
    void unknownEventType_silentlyIgnored() {
        // IncidentUpdated with unknown action produces Optional.empty() from factory
        DomainEvent event = new IncidentUpdated(
                NOW, "actor-1", "INC-001",
                IncidentStatus.IN_PROGRESS, IncidentStatus.IN_PROGRESS,
                "Some note", "SOME_UNKNOWN_ACTION_XYZ"
        );

        AtomicInteger sendCount = new AtomicInteger(0);
        NotificationChannel mockChannel = new NotificationChannel() {
            @Override public String channelName() { return "TEST"; }
            @Override public void send(Notification n) { sendCount.incrementAndGet(); }
        };

        listener = new NotificationEventListener(factory, resolver, List.of(mockChannel));

        // Should not throw, should not call channel
        assertDoesNotThrow(() -> listener.onEvent(event));
        assertEquals(0, sendCount.get(), "No notifications should be sent for unknown event type");
    }

    @Test
    @DisplayName("One failing channel does not block the other channel")
    void failingChannel_doesNotBlockOtherChannels() {
        Administrator admin1 = TestData.admin("admin-1");
        userRepository.save(admin1);

        DomainEvent event = new IncidentCreated(
                NOW, "actor-99", "INC-001", null, IncidentStatus.NEW,
                "Ticket opened", "reporter-1", "srv-1", "cat-1",
                null, CategorySource.MANUAL, PriorityLevel.P1, 80
        );

        // Channel 1 throws
        NotificationChannel failingChannel = new NotificationChannel() {
            @Override public String channelName() { return "FAILING"; }
            @Override public void send(Notification n) { throw new RuntimeException("channel failure"); }
        };

        // Channel 2 records
        List<Notification> received = new ArrayList<>();
        NotificationChannel goodChannel = new NotificationChannel() {
            @Override public String channelName() { return "GOOD"; }
            @Override public void send(Notification n) { received.add(n); }
        };

        listener = new NotificationEventListener(factory, resolver, List.of(failingChannel, goodChannel));

        assertDoesNotThrow(() -> listener.onEvent(event));

        // Good channel should still have been called
        assertFalse(received.isEmpty(), "Good channel should have received notification");
    }

    @Test
    @DisplayName("Inactive users are skipped — not sent notifications")
    void inactiveUsers_areSkipped() {
        Administrator activeAdmin = TestData.admin("admin-active");
        Administrator inactiveAdmin = TestData.admin("admin-inactive");
        userRepository.save(activeAdmin);
        userRepository.save(inactiveAdmin);
        inactiveAdmin.deactivate();

        DomainEvent event = new IncidentCreated(
                NOW, "actor-99", "INC-001", null, IncidentStatus.NEW,
                "Ticket opened", "reporter-1", "srv-1", "cat-1",
                null, CategorySource.MANUAL, PriorityLevel.P1, 80
        );

        List<Notification> received = new ArrayList<>();
        NotificationChannel channel = new NotificationChannel() {
            @Override public String channelName() { return "TEST"; }
            @Override public void send(Notification n) { received.add(n); }
        };

        listener = new NotificationEventListener(factory, resolver, List.of(channel));
        listener.onEvent(event);

        // Only active admin should receive
        assertEquals(1, received.size());
        assertEquals("admin-active", received.get(0).recipientId());
    }

    @Test
    @DisplayName("Actor is never notified of their own action")
    void actor_isNeverNotified() {
        Administrator actorAdmin = TestData.admin("admin-actor");
        Administrator otherAdmin = TestData.admin("admin-other");
        userRepository.save(actorAdmin);
        userRepository.save(otherAdmin);

        DomainEvent event = new IncidentCreated(
                NOW, "admin-actor", "INC-001", null, IncidentStatus.NEW,
                "Ticket opened", "reporter-1", "srv-1", "cat-1",
                null, CategorySource.MANUAL, PriorityLevel.P1, 80
        );

        List<Notification> received = new ArrayList<>();
        NotificationChannel channel = new NotificationChannel() {
            @Override public String channelName() { return "TEST"; }
            @Override public void send(Notification n) { received.add(n); }
        };

        listener = new NotificationEventListener(factory, resolver, List.of(channel));
        listener.onEvent(event);

        // Actor should not receive a notification
        for (Notification n : received) {
            assertNotEquals("admin-actor", n.recipientId(), "Actor should not be notified of their own action");
        }
        // Other admin should receive
        assertTrue(received.stream().anyMatch(n -> "admin-other".equals(n.recipientId())));
    }
}
