package com.iitsm.infrastructure.persistence.jpa;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.notification.NotificationRepository;

/**
 * JPA implementation of the domain {@link NotificationRepository}. Save is an upsert keyed by the
 * notification id (an existing row keeps its insertion position); markRead on an unknown id is a no-op,
 * matching the in-memory implementation.
 */
@Repository
@Transactional
public class JpaNotificationRepository implements NotificationRepository {

    private final NotificationEntityRepository entities;

    public JpaNotificationRepository(NotificationEntityRepository entities) {
        this.entities = entities;
    }

    @Override
    public void save(Notification notification) {
        if (notification == null) {
            throw new InvalidInputException("notification must not be null");
        }
        NotificationEntity entity = entities.findByNotificationId(notification.id())
                .orElseGet(NotificationEntity::new);
        entity.copyFrom(notification);
        entities.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> findByRecipientId(String recipientId) {
        requireRecipient(recipientId);
        return entities.findByRecipientIdOrderBySeqAsc(recipientId).stream()
                .map(NotificationEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> findUnreadByRecipientId(String recipientId) {
        requireRecipient(recipientId);
        return entities.findByRecipientIdAndReadFlagFalseOrderBySeqAsc(recipientId).stream()
                .map(NotificationEntity::toDomain).toList();
    }

    @Override
    public void markRead(String notificationId) {
        if (notificationId == null || notificationId.isBlank()) {
            throw new InvalidInputException("notificationId must not be blank");
        }
        entities.findByNotificationId(notificationId).ifPresent(e -> {
            e.readFlag = true;
            entities.save(e);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> findAll() {
        return entities.findAllByOrderBySeqAsc().stream().map(NotificationEntity::toDomain).toList();
    }

    private static void requireRecipient(String recipientId) {
        if (recipientId == null || recipientId.isBlank()) {
            throw new InvalidInputException("recipientId must not be blank");
        }
    }
}
