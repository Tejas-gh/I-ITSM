package com.iitsm.api;

import com.iitsm.api.dto.NotificationResponse;
import com.iitsm.application.service.NotificationQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for retrieving and managing user notifications.
 * Notifications are scoped to the authenticated user.
 */
@RestController
@RequestMapping("/api/notifications")
@Transactional
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    public NotificationController(NotificationQueryService notificationQueryService) {
        this.notificationQueryService = notificationQueryService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Authentication authentication) {
        return notificationQueryService.getNotificationsForUser(authentication.getName())
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @GetMapping("/unread")
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications(Authentication authentication) {
        return notificationQueryService.getUnreadNotificationsForUser(authentication.getName())
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @RequestMapping(value = "/{id}/read", method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<Void> markAsRead(@PathVariable String id) {
        notificationQueryService.markNotificationRead(id);
        return ResponseEntity.noContent().build();
    }
}
