package com.iitsm.infrastructure.event;

import com.iitsm.domain.event.AssignmentFailed;
import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.DomainEventListener;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.IncidentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryDomainEventPublisherTest {

    private InMemoryDomainEventPublisher publisher;
    private DomainEvent testEvent;

    @BeforeEach
    void setUp() {
        publisher = new InMemoryDomainEventPublisher();
        testEvent = new AssignmentFailed(
                Instant.parse("2026-11-10T12:00:00Z"),
                DomainEvent.SYSTEM_ACTOR,
                "INC1001",
                IncidentStatus.CLASSIFIED,
                IncidentStatus.CLASSIFIED,
                "No candidate technician available"
        );
    }

    @Test
    @DisplayName("subscribe and publish preserve subscription order")
    void publish_multipleSubscribers_deliveredInSubscriptionOrder() {
        List<String> callLog = new ArrayList<>();

        publisher.subscribe(event -> callLog.add("first"));
        publisher.subscribe(event -> callLog.add("second"));
        publisher.subscribe(event -> callLog.add("third"));

        publisher.publish(testEvent);

        assertEquals(List.of("first", "second", "third"), callLog);
    }

    @Test
    @DisplayName("all subscribers are called for a published event")
    void publish_registeredSubscribers_allReceivePublishedEvent() {
        List<DomainEvent> firstReceived = new ArrayList<>();
        List<DomainEvent> secondReceived = new ArrayList<>();

        publisher.subscribe(firstReceived::add);
        publisher.subscribe(secondReceived::add);

        publisher.publish(testEvent);

        assertEquals(1, firstReceived.size());
        assertEquals(testEvent, firstReceived.get(0));
        assertEquals(1, secondReceived.size());
        assertEquals(testEvent, secondReceived.get(0));
    }

    @Test
    @DisplayName("non-critical listener failure is isolated and delivery continues to other listeners")
    void publish_nonCriticalListenerThrows_deliveryContinuesToRemainingListeners() {
        List<String> callLog = new ArrayList<>();

        publisher.subscribe(event -> {
            callLog.add("failing-listener");
            throw new RuntimeException("Simulated listener failure");
        });

        publisher.subscribe(event -> callLog.add("successful-listener"));

        assertDoesNotThrow(() -> publisher.publish(testEvent));
        assertEquals(List.of("failing-listener", "successful-listener"), callLog);
    }

    @Test
    @DisplayName("critical listener failure propagates immediately and aborts remaining delivery")
    void publish_criticalListenerThrows_exceptionPropagatesImmediately() {
        List<String> callLog = new ArrayList<>();

        publisher.subscribe(new DomainEventListener() {
            @Override
            public void onEvent(DomainEvent event) {
                callLog.add("critical-listener");
                throw new IllegalStateException("Critical failure");
            }

            @Override
            public boolean critical() {
                return true;
            }
        });

        publisher.subscribe(event -> callLog.add("subsequent-listener"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> publisher.publish(testEvent)
        );
        assertEquals("Critical failure", ex.getMessage());
        assertEquals(List.of("critical-listener"), callLog);
    }

    @Test
    @DisplayName("null event or listener throws InvalidInputException")
    void subscribeAndPublish_nullArguments_throwInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> publisher.subscribe(null));
        assertThrows(InvalidInputException.class, () -> publisher.publish(null));
    }
}
