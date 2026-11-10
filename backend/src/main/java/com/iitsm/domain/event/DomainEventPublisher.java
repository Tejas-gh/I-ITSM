package com.iitsm.domain.event;

/**
 * Publisher contract for distributing domain events to registered listeners.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);

    void subscribe(DomainEventListener listener);
}
