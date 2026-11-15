package com.iitsm.integration;

import com.iitsm.application.command.AssignIncidentCommand;
import com.iitsm.application.command.CreateIncidentCommand;
import com.iitsm.application.command.IncidentTextCommand;
import com.iitsm.application.service.IncidentService;
import com.iitsm.application.service.NotificationQueryService;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.repository.ItServiceRepository;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate B7: End-to-end integration test proving notification generation, delivery, and lifecycle over JPA.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:b7-notification;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@DirtiesContext
@Transactional
public class NotificationIntegrationTest {

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private NotificationQueryService notificationQueryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ItServiceRepository itServiceRepository;

    private static final String EMP_ID = "EMP_NOTIF_1";
    private static final String TECH_ID = "TECH_NOTIF_1";
    private static final String ADMIN_ID = "ADMIN_NOTIF_1";
    private static final String SERVICE_ID = "SVC_NOTIF_APP";

    private Technician technician;

    @BeforeEach
    void setUp() {
        Employee employee = TestData.employee(EMP_ID);
        technician = TestData.technician(TECH_ID, 10);
        Administrator admin = TestData.admin(ADMIN_ID);
        ItService service = TestData.service(SERVICE_ID, BusinessCriticality.MEDIUM);

        userRepository.save(employee);
        userRepository.save(technician);
        userRepository.save(admin);
        itServiceRepository.save(service);
    }

    @Test
    void incidentLifecycle_deliversAndManagesNotifications() {
        // Temporarily set technician unavailable to prevent auto-assign during creation,
        // allowing manual assignment notification to be tested cleanly
        technician.markUnavailable();
        userRepository.save(technician);

        // 1. Create incident
        CreateIncidentCommand createCmd = new CreateIncidentCommand(
                EMP_ID,
                "Email service latency",
                "Messages take 20 minutes to send",
                SERVICE_ID,
                "APPLICATION",
                null,
                CategorySource.MANUAL,
                Impact.MEDIUM,
                Urgency.MEDIUM,
                10
        );
        Incident incident = incidentService.createIncident(createCmd);
        String incId = incident.getId();

        // 2. Make technician available and assign to technician
        technician.markAvailable();
        userRepository.save(technician);

        AssignIncidentCommand assignCmd = new AssignIncidentCommand(ADMIN_ID, incId, TECH_ID);
        incidentService.assignIncident(assignCmd);

        // Verify technician received assignment notification
        List<Notification> techNotifications = notificationQueryService.getNotificationsForUser(TECH_ID);
        assertFalse(techNotifications.isEmpty(), "Technician should have received assignment notification");

        Notification assignNotification = techNotifications.stream()
                .filter(n -> n.incidentId().equals(incId))
                .findFirst()
                .orElseThrow();

        assertEquals(TECH_ID, assignNotification.recipientId());
        assertEquals("IN_APP", assignNotification.channelName());
        assertFalse(assignNotification.read());
        assertNotNull(assignNotification.title());
        assertNotNull(assignNotification.body());

        // Check unread notifications query
        List<Notification> unreadTech = notificationQueryService.getUnreadNotificationsForUser(TECH_ID);
        assertTrue(unreadTech.stream().anyMatch(n -> n.id().equals(assignNotification.id())));

        // 3. Mark notification as read
        notificationQueryService.markNotificationRead(assignNotification.id());

        List<Notification> unreadAfterRead = notificationQueryService.getUnreadNotificationsForUser(TECH_ID);
        assertFalse(unreadAfterRead.stream().anyMatch(n -> n.id().equals(assignNotification.id())));

        // 4. Technician acknowledges and resolves incident
        incidentService.acknowledgeIncident(TECH_ID, incId);
        incidentService.startWork(TECH_ID, incId);
        incidentService.resolveIncident(new IncidentTextCommand(TECH_ID, incId, "Cleared message queues"));

        // Verify admin received resolution notification
        List<Notification> adminNotifications = notificationQueryService.getNotificationsForUser(ADMIN_ID);
        assertFalse(adminNotifications.isEmpty(), "Admin should have received resolution notification");

        Notification resolutionNotification = adminNotifications.stream()
                .filter(n -> n.incidentId().equals(incId))
                .findFirst()
                .orElseThrow();

        assertEquals(ADMIN_ID, resolutionNotification.recipientId());
        assertNotNull(resolutionNotification.title());

        // 5. Verification and closure
        incidentService.verifyResolution(EMP_ID, incId);
        incidentService.closeIncident(new com.iitsm.application.command.CloseIncidentCommand(ADMIN_ID, incId, "Closing confirmed"));

        // Verify reporter received closure notification (CLOSED notifies assignee + admins + reporter)
        List<Notification> empNotifications = notificationQueryService.getNotificationsForUser(EMP_ID);
        assertFalse(empNotifications.isEmpty(), "Reporter should have received closure notification");
        assertTrue(empNotifications.stream().anyMatch(n -> n.incidentId().equals(incId)));

        // Verify user recipient isolation
        assertTrue(techNotifications.stream().noneMatch(n -> n.recipientId().equals(EMP_ID)));
    }
}
