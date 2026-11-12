package com.iitsm.infrastructure.persistence.jpa;

import java.time.Instant;

import com.iitsm.domain.notification.Notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA structure for {@code notifications} (V3); {@code seq} is the insertion-order key. */
@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long seq;
    String notificationId;
    String recipientId;
    String title;
    String body;
    String incidentId;
    String channelName;
    @Column(name = "is_read")
    boolean readFlag;
    Instant createdAt;

    protected NotificationEntity() {
    }

    void copyFrom(Notification n) {
        this.notificationId = n.id();
        this.recipientId = n.recipientId();
        this.title = n.title();
        this.body = n.body();
        this.incidentId = n.incidentId();
        this.channelName = n.channelName();
        this.readFlag = n.read();
        this.createdAt = n.createdAt();
    }

    static NotificationEntity from(Notification n) {
        NotificationEntity e = new NotificationEntity();
        e.copyFrom(n);
        return e;
    }

    Notification toDomain() {
        return new Notification(notificationId, recipientId, title, body, incidentId, channelName, readFlag, createdAt);
    }
}
