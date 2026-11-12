package com.iitsm.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access for {@link NotificationEntity}; used only by the JPA adapters. */
public interface NotificationEntityRepository extends JpaRepository<NotificationEntity, Long> {

    Optional<NotificationEntity> findByNotificationId(String notificationId);

    List<NotificationEntity> findByRecipientIdOrderBySeqAsc(String recipientId);

    List<NotificationEntity> findByRecipientIdAndReadFlagFalseOrderBySeqAsc(String recipientId);

    List<NotificationEntity> findAllByOrderBySeqAsc();
}
