package com.iitsm.infrastructure.persistence.memory;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.notification.NotificationRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thread-safe in-memory implementation of {@link NotificationRepository}.
 * Uses a synchronized {@link LinkedHashMap} keyed by notification id for O(1) lookup.
 */
public final class InMemoryNotificationRepository implements NotificationRepository {

    private final Map<String, Notification> store = new LinkedHashMap<>();

    @Override
    public synchronized void save(Notification notification) {
        if (notification == null) {
            throw new InvalidInputException("notification must not be null");
        }
        store.put(notification.id(), notification);
    }

    @Override
    public synchronized List<Notification> findByRecipientId(String recipientId) {
        if (recipientId == null || recipientId.isBlank()) {
            throw new InvalidInputException("recipientId must not be blank");
        }
        List<Notification> result = new ArrayList<>();
        for (Notification n : store.values()) {
            if (recipientId.equals(n.recipientId())) {
                result.add(n);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized List<Notification> findUnreadByRecipientId(String recipientId) {
        if (recipientId == null || recipientId.isBlank()) {
            throw new InvalidInputException("recipientId must not be blank");
        }
        List<Notification> result = new ArrayList<>();
        for (Notification n : store.values()) {
            if (recipientId.equals(n.recipientId()) && !n.read()) {
                result.add(n);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized void markRead(String notificationId) {
        if (notificationId == null || notificationId.isBlank()) {
            throw new InvalidInputException("notificationId must not be blank");
        }
        Notification existing = store.get(notificationId);
        if (existing != null) {
            store.put(notificationId, existing.withRead(true));
        }
    }

    @Override
    public synchronized List<Notification> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(store.values()));
    }
}
