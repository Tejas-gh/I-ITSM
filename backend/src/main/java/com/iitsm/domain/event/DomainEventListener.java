package com.iitsm.domain.event;

/**
 * Observer contract for consuming domain events.
 */
public interface DomainEventListener {

    void onEvent(DomainEvent event);

    default boolean critical() {
        return false;
    }
}
