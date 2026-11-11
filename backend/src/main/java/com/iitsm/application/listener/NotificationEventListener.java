package com.iitsm.application.listener;

import com.iitsm.application.notification.NotificationRecipientResolver;
import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.DomainEventListener;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.notification.NotificationChannel;
import com.iitsm.domain.notification.NotificationFactory;

import java.util.List;
import java.util.UUID;

/**
 * Non-critical event listener that creates and dispatches notifications for domain events.
 * A failing channel does not block others; failures are logged at WARNING level.
 */
public final class NotificationEventListener implements DomainEventListener {

    private static final System.Logger LOGGER =
            System.getLogger(NotificationEventListener.class.getName());

    private final NotificationFactory factory;
    private final NotificationRecipientResolver resolver;
    private final List<NotificationChannel> channels;

    public NotificationEventListener(NotificationFactory factory,
                                     NotificationRecipientResolver resolver,
                                     List<NotificationChannel> channels) {
        if (factory == null) {
            throw new InvalidInputException("factory must not be null");
        }
        if (resolver == null) {
            throw new InvalidInputException("resolver must not be null");
        }
        if (channels == null) {
            throw new InvalidInputException("channels must not be null");
        }
        this.factory = factory;
        this.resolver = resolver;
        this.channels = channels;
    }

    @Override
    public void onEvent(DomainEvent event) {
        if (event == null) {
            return;
        }

        // 1. Get template — if empty (unknown event), return silently
        var templateOpt = factory.templateFor(event);
        if (templateOpt.isEmpty()) {
            return;
        }
        var template = templateOpt.get();

        // 2. Get recipient IDs from resolver
        List<String> recipientIds = resolver.resolveRecipientIds(event);

        // 3. For each recipient, for each channel — create and send notification
        for (String recipientId : recipientIds) {
            for (NotificationChannel channel : channels) {
                Notification notification = new Notification(
                        UUID.randomUUID().toString(),
                        recipientId,
                        template.title(),
                        template.body(),
                        event.incidentId(),
                        channel.channelName(),
                        false,
                        event.occurredAt()
                );
                try {
                    channel.send(notification);
                } catch (Exception ex) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Channel " + channel.channelName()
                                    + " failed to send notification to " + recipientId
                                    + ": " + ex.getMessage());
                }
            }
        }
    }

    @Override
    public boolean critical() {
        return false;
    }
}
