package com.iitsm.infrastructure.event;

import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.DomainEventListener;
import com.iitsm.domain.event.DomainEventPublisher;
import com.iitsm.domain.exception.InvalidInputException;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Synchronous in-memory implementation of {@link DomainEventPublisher}.
 * Maintains listeners in subscription order. Non-critical listener failures are
 * logged at WARNING level and delivery continues; critical failures propagate immediately.
 */
public final class InMemoryDomainEventPublisher implements DomainEventPublisher {

    private static final System.Logger LOGGER = System.getLogger(InMemoryDomainEventPublisher.class.getName());

    private final CopyOnWriteArrayList<DomainEventListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void subscribe(DomainEventListener listener) {
        if (listener == null) {
            throw new InvalidInputException("listener must not be null");
        }
        listeners.add(listener);
    }

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            throw new InvalidInputException("event must not be null");
        }
        for (DomainEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Throwable t) {
                if (listener.critical()) {
                    if (t instanceof RuntimeException re) {
                        throw re;
                    } else if (t instanceof Error err) {
                        throw err;
                    } else {
                        throw new RuntimeException(t);
                    }
                }
                LOGGER.log(System.Logger.Level.WARNING, "Non-critical listener failed on event: " + event.action(), t);
            }
        }
    }
}
