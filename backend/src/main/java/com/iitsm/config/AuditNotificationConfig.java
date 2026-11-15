package com.iitsm.config;

import com.iitsm.application.listener.AuditEventListener;
import com.iitsm.application.listener.NotificationEventListener;
import com.iitsm.application.notification.InAppNotificationChannel;
import com.iitsm.application.notification.NotificationRecipientResolver;
import com.iitsm.application.notification.SimulatedEmailNotificationChannel;
import com.iitsm.application.service.NotificationQueryService;
import com.iitsm.domain.audit.AuditRepository;
import com.iitsm.domain.event.DomainEventPublisher;
import com.iitsm.domain.notification.NotificationChannel;
import com.iitsm.domain.notification.NotificationFactory;
import com.iitsm.domain.notification.NotificationRepository;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Gate B5: Wiring for audit and notification event listeners and services.
 * Ensures the audit listener is subscribed first, followed by the notification listener.
 */
@Configuration
public class AuditNotificationConfig {

    private final DomainEventPublisher publisher;
    private final AuditEventListener auditListener;
    private final NotificationEventListener notificationListener;

    public AuditNotificationConfig(DomainEventPublisher publisher,
                                   AuditRepository auditRepository,
                                   NotificationRepository notificationRepository,
                                   UserRepository userRepository,
                                   IncidentRepository incidentRepository) {
        this.publisher = publisher;
        this.auditListener = new AuditEventListener(auditRepository);

        NotificationFactory factory = new NotificationFactory();
        NotificationRecipientResolver resolver = new NotificationRecipientResolver(userRepository, incidentRepository);
        List<NotificationChannel> channels = List.of(
                new InAppNotificationChannel(notificationRepository),
                new SimulatedEmailNotificationChannel()
        );
        this.notificationListener = new NotificationEventListener(factory, resolver, channels);
    }

    @PostConstruct
    public void registerListeners() {
        // Audit listener subscribes FIRST, followed by notification listener
        publisher.subscribe(auditListener);
        publisher.subscribe(notificationListener);
    }

    @Bean
    public AuditEventListener auditEventListener() {
        return auditListener;
    }

    @Bean
    public NotificationEventListener notificationEventListener() {
        return notificationListener;
    }

    @Bean
    public NotificationQueryService notificationQueryService(NotificationRepository notificationRepository) {
        return new NotificationQueryService(notificationRepository);
    }
}
