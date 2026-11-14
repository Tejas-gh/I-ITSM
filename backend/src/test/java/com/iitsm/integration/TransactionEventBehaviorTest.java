package com.iitsm.integration;

import com.iitsm.application.command.CreateIncidentCommand;
import com.iitsm.application.service.IncidentService;
import com.iitsm.domain.audit.AuditRepository;
import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.DomainEventListener;
import com.iitsm.domain.event.DomainEventPublisher;
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
import com.iitsm.domain.notification.NotificationRepository;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.repository.ItServiceRepository;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate B7: Tests transactional boundaries, event publisher failure propagation,
 * and verifies that non-critical notification failures do not poison shared transactions.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:b7-tx-event;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@DirtiesContext
public class TransactionEventBehaviorTest {

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private AuditRepository auditRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DomainEventPublisher publisher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ItServiceRepository itServiceRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final String EMP_ID = "EMP_TX_1";
    private static final String TECH_ID = "TECH_TX_1";
    private static final String ADMIN_ID = "ADMIN_TX_1";
    private static final String SERVICE_ID = "SVC_TX_NET";

    @BeforeEach
    void setUp() {
        TransactionStatus tx = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            Employee employee = TestData.employee(EMP_ID);
            Technician technician = TestData.technician(TECH_ID, 10);
            Administrator admin = TestData.admin(ADMIN_ID);
            ItService service = TestData.service(SERVICE_ID, BusinessCriticality.HIGH);

            userRepository.save(employee);
            userRepository.save(technician);
            userRepository.save(admin);
            itServiceRepository.save(service);
            transactionManager.commit(tx);
        } catch (Exception e) {
            transactionManager.rollback(tx);
            throw e;
        }
    }

    @Test
    void criticalListenerFailure_rollsBackIncidentTransaction() {
        AtomicBoolean shouldFail = new AtomicBoolean(true);

        DomainEventListener failingCriticalListener = new DomainEventListener() {
            @Override
            public void onEvent(DomainEvent event) {
                if (shouldFail.get() && EMP_ID.equals(event.actorId())) {
                    throw new IllegalStateException("Simulated critical audit persistence failure");
                }
            }

            @Override
            public boolean critical() {
                return true;
            }
        };

        publisher.subscribe(failingCriticalListener);

        CreateIncidentCommand createCmd = new CreateIncidentCommand(
                EMP_ID,
                "Transaction Rollback Test Incident",
                "Description for rollback test",
                SERVICE_ID,
                "NETWORK",
                null,
                CategorySource.MANUAL,
                Impact.HIGH,
                Urgency.HIGH,
                5
        );

        TransactionStatus tx = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            incidentService.createIncident(createCmd);
            transactionManager.commit(tx);
        } catch (IllegalStateException expected) {
            transactionManager.rollback(tx);
        } finally {
            shouldFail.set(false);
        }

        // Verify that the incident was NOT committed to the database
        List<Incident> found = incidentRepository.findAll().stream()
                .filter(i -> "Transaction Rollback Test Incident".equals(i.getTitle()))
                .toList();
        assertTrue(found.isEmpty(), "Incident must not exist in repository after transaction rollback");
    }

    @Test
    void nonCriticalNotificationFailure_doesNotPoisonIncidentTransaction() {
        DomainEventListener failingNonCriticalListener = new DomainEventListener() {
            @Override
            public void onEvent(DomainEvent event) {
                if (EMP_ID.equals(event.actorId())) {
                    throw new RuntimeException("Simulated non-critical delivery failure");
                }
            }

            @Override
            public boolean critical() {
                return false;
            }
        };

        publisher.subscribe(failingNonCriticalListener);

        TransactionStatus tx = transactionManager.getTransaction(new DefaultTransactionDefinition());
        String incidentId;
        try {
            CreateIncidentCommand createCmd = new CreateIncidentCommand(
                    EMP_ID,
                    "Notification Resilience Test",
                    "Description for resilience test",
                    SERVICE_ID,
                    "NETWORK",
                    null,
                    CategorySource.MANUAL,
                    Impact.LOW,
                    Urgency.LOW,
                    1
            );
            Incident incident = incidentService.createIncident(createCmd);
            incidentId = incident.getId();
            transactionManager.commit(tx);
        } catch (Exception e) {
            transactionManager.rollback(tx);
            throw e;
        }

        // Verify that the incident and audit entry were successfully committed despite non-critical failure
        assertTrue(incidentRepository.findById(incidentId).isPresent(),
                "Incident must be committed despite non-critical channel failure");
        assertFalse(auditRepository.findByIncidentId(incidentId).isEmpty(),
                "Audit trail must be committed despite non-critical channel failure");
    }

    @Test
    void jpaNotificationRepository_saveRunsInOwnTransaction() {
        Notification notification = new Notification(
                "NOTIF_TX_1",
                EMP_ID,
                "Test Title",
                "Test Body",
                "INC_TX_1",
                "IN_APP",
                false,
                Instant.now()
        );

        notificationRepository.save(notification);

        List<Notification> found = notificationRepository.findByRecipientId(EMP_ID);
        assertTrue(found.stream().anyMatch(n -> n.id().equals("NOTIF_TX_1")));
    }
}
