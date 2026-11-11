package com.iitsm.application.service;

import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.DomainEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecordingListener implements DomainEventListener {

    private final List<DomainEvent> events = new ArrayList<>();

    @Override
    public void onEvent(DomainEvent event) {
        events.add(event);
    }

    public List<DomainEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    @SuppressWarnings("unchecked")
    public <T extends DomainEvent> List<T> getEventsOfType(Class<T> type) {
        List<T> matched = new ArrayList<>();
        for (DomainEvent e : events) {
            if (type.isInstance(e)) {
                matched.add((T) e);
            }
        }
        return Collections.unmodifiableList(matched);
    }

    public void clear() {
        events.clear();
    }
}
