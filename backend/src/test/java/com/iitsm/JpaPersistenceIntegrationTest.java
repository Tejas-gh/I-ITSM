package com.iitsm.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.domain.audit.AuditEntry;
import com.iitsm.domain.audit.AuditRepository;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentDraft;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.model.NoteType;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.SlaPolicy;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.model.User;
import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.notification.NotificationRepository;
import com.iitsm.domain.priority.PriorityResult;
import com.iitsm.domain.repository.CategoryRepository;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.repository.IncidentSearchCriteria;
import com.iitsm.domain.repository.ItServiceRepository;
import com.iitsm.domain.repository.SlaPolicyRepository;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.support.TestData;

import jakarta.persistence.EntityManager;

/**
 * A4 integration tests: domain repositories backed by JPA on H2 (PostgreSQL mode) with the Flyway schema.
 * Each test runs in a transaction that is rolled back, so the shared in-memory database stays clean.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JpaPersistenceIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-16T09:00:00Z");

    @Autowired private CategoryRepository categories;
    @Autowired private ItServiceRepository services;
    @Autowired private SlaPolicyRepository slaPolicies;
    @Autowired private UserRepository users;
    @Autowired private IncidentRepository incidents;
    @Autowired private AuditRepository audits;
    @Autowired private NotificationRepository notifications;
    @Autowired private EntityManager em;

    private User employee;
    private User admin;
    private Technician tech;
    private ItService service1;

    @BeforeEach
    void baseData() {
        employee = users.save(TestData.employee("E1"));
        admin = users.save(TestData.admin("A1"));
        tech = (Technician) users.save(TestData.technician("T1", 3));
        service1 = services.save(TestData.service("SVC1", BusinessCriticality.HIGH));
        flushAndClear();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private Incident newIncident(String id, ItService service, String categoryId, Instant created, PriorityResult p) {
        IncidentDraft draft = TestData.draft(employee, service, TestData.category(categoryId),
                Impact.HIGH, Urgency.HIGH, 5);
        return new Incident(id, draft, p, created);
    }

    private static List<String> ids(List<Incident> list) {
        return list.stream().map(Incident::getId).toList();
    }

    // ---------- categories ----------

    @Test
    void category_saveReadDeactivate_roundTripsAndFiltersActive() {
        assertEquals(Optional.empty(), categories.findById("CUSTOM"));
        Category custom = new Category("CUSTOM", "Custom Category");
        categories.save(custom);
        flushAndClear();

        Category loaded = categories.findById("CUSTOM").orElseThrow();
        assertEquals("Custom Category", loaded.getName());
        assertTrue(loaded.isActive());
        assertEquals(10, categories.findAll().size());
        assertEquals(10, categories.findAllActive().size());

        loaded.deactivate();
        categories.save(loaded);
        flushAndClear();

        assertFalse(categories.findById("CUSTOM").orElseThrow().isActive());
        assertEquals(9, categories.findAllActive().size());
        assertTrue(categories.findAllActive().stream().noneMatch(c -> c.getId().equals("CUSTOM")));
        assertTrue(categories.findById("NETWORK").isPresent());   // seeded by V2
    }

    // ---------- IT services ----------

    @Test
    void itService_saveAndRead_roundTrips() {
        services.save(new ItService("SVC9", "Payments", BusinessCriticality.CRITICAL));
        flushAndClear();

        ItService loaded = services.findById("SVC9").orElseThrow();
        assertEquals("Payments", loaded.getName());
        assertEquals(BusinessCriticality.CRITICAL, loaded.getBusinessCriticality());
        assertEquals(List.of("SVC1", "SVC9"), services.findAll().stream().map(ItService::getId).toList());
        assertTrue(services.findById("NOPE").isEmpty());
        assertThrows(InvalidInputException.class, () -> services.findById(null));
    }

    // ---------- SLA policies ----------

    @Test
    void slaPolicy_seededDefaults_areReadable() {
        SlaPolicy p1 = slaPolicies.findDefault(PriorityLevel.P1).orElseThrow();
        assertTrue(p1.isDefault());
        assertEquals(Duration.ofMinutes(5), p1.responseTarget());
        assertEquals(Duration.ofMinutes(30), p1.resolutionTarget());
        SlaPolicy p4 = slaPolicies.findDefault(PriorityLevel.P4).orElseThrow();
        assertEquals(Duration.ofHours(4), p4.responseTarget());
        assertEquals(Duration.ofHours(24), p4.resolutionTarget());
        assertEquals(4, slaPolicies.findAll().size());
        assertTrue(slaPolicies.findOverride("SVC1", PriorityLevel.P1).isEmpty());
    }

    @Test
    void slaPolicy_overrideSaveAndReplace_keepsSingleRowPerKey() {
        slaPolicies.save(new SlaPolicy("SVC1", PriorityLevel.P1, Duration.ofSeconds(60), Duration.ofSeconds(120)));
        flushAndClear();
        slaPolicies.save(new SlaPolicy("SVC1", PriorityLevel.P1, Duration.ofSeconds(90), Duration.ofSeconds(200)));
        flushAndClear();

        SlaPolicy override = slaPolicies.findOverride("SVC1", PriorityLevel.P1).orElseThrow();
        assertEquals("SVC1", override.serviceId());
        assertEquals(Duration.ofSeconds(90), override.responseTarget());
        assertEquals(Duration.ofSeconds(200), override.resolutionTarget());
        assertEquals(1, slaPolicies.findAll().stream().filter(p -> "SVC1".equals(p.serviceId())).count());
        assertEquals(Duration.ofMinutes(5), slaPolicies.findDefault(PriorityLevel.P1).orElseThrow().responseTarget());
    }

    @Test
    void slaPolicy_replaceDefault_andNullArguments() {
        slaPolicies.save(new SlaPolicy(null, PriorityLevel.P2, Duration.ofMinutes(10), Duration.ofHours(1)));
        flushAndClear();
        SlaPolicy p2 = slaPolicies.findDefault(PriorityLevel.P2).orElseThrow();
        assertEquals(Duration.ofMinutes(10), p2.responseTarget());
        assertEquals(4, slaPolicies.findAll().size());
        assertThrows(InvalidInputException.class, () -> slaPolicies.findOverride(null, PriorityLevel.P1));
        assertThrows(InvalidInputException.class, () -> slaPolicies.findDefault(null));
    }

    // ---------- users and technician skills ----------

    @Test
    void user_employeeAndAdministrator_reconstructCorrectTypes() {
        users.save(new Administrator("A2", "Boss", "boss@x.com", Role.IT_MANAGER));
        flushAndClear();

        User e = users.findById("E1").orElseThrow();
        assertInstanceOf(com.iitsm.domain.model.Employee.class, e);
        assertEquals("Finance", ((com.iitsm.domain.model.Employee) e).getDepartment());
        User a = users.findById("A2").orElseThrow();
        assertInstanceOf(Administrator.class, a);
        assertEquals(Role.IT_MANAGER, a.getRole());
        assertTrue(users.findById("MISSING").isEmpty());
    }

    @Test
    void user_technicianWithSkills_roundTripsAndSkillsAreReplaced() {
        Technician lead = new Technician("T3", "Tess", "tess@x.com", Role.TEAM_LEAD,
                Map.of("NETWORK", 4, "CLOUD", 1), 7, 5);
        lead.markUnavailable();
        lead.deactivate();
        users.save(lead);
        flushAndClear();

        Technician loaded = (Technician) users.findById("T3").orElseThrow();
        assertEquals(Role.TEAM_LEAD, loaded.getRole());
        assertEquals(Map.of("NETWORK", 4, "CLOUD", 1), loaded.getSkillLevels());
        assertEquals(7, loaded.getYearsOfExperience());
        assertEquals(5, loaded.getMaxActiveIncidents());
        assertFalse(loaded.isAvailable());
        assertFalse(loaded.isActive());

        users.save(new Technician("T3", "Tess", "tess@x.com", Role.TEAM_LEAD, Map.of("DATABASE", 5), 7, 5));
        flushAndClear();
        Technician replaced = (Technician) users.findById("T3").orElseThrow();
        assertEquals(Map.of("DATABASE", 5), replaced.getSkillLevels());
        assertTrue(replaced.isAvailable());
        assertTrue(replaced.isActive());
    }

    @Test
    void user_findAllTechnicians_returnsOnlyTechnicianRoles() {
        users.save(new Technician("T3", "Tess", "tess@x.com", Role.TEAM_LEAD, Map.of(), 2, 4));
        flushAndClear();

        assertEquals(List.of("T1", "T3"), users.findAllTechnicians().stream().map(Technician::getId).toList());
        assertEquals(List.of("A1", "E1", "T1", "T3"), users.findAll().stream().map(User::getId).toList());
        assertEquals(Map.of("NETWORK", 3, "DATABASE", 2), users.findAllTechnicians().get(0).getSkillLevels());
    }

    // ---------- incidents ----------

    @Test
    void incident_newIncident_roundTripsWithSuggestedCategory() {
        Category network = categories.findById("NETWORK").orElseThrow();
        IncidentDraft draft = new IncidentDraft("Wifi down", "No wifi on floor 2", employee, service1,
                network, network, CategorySource.KEYWORD, Impact.MEDIUM, Urgency.HIGH, 12);
        Incident created = new Incident("INC1001", draft, new PriorityResult(22, PriorityLevel.P2), T0);
        incidents.save(created);
        flushAndClear();

        Incident loaded = incidents.findById("INC1001").orElseThrow();
        assertEquals(IncidentStatus.NEW, loaded.getStatus());
        assertEquals("Wifi down", loaded.getTitle());
        assertEquals("No wifi on floor 2", loaded.getDescription());
        assertEquals("E1", loaded.getReporter().getId());
        assertEquals("SVC1", loaded.getService().getId());
        assertEquals("NETWORK", loaded.getCategory().getId());
        assertEquals("NETWORK", loaded.getSuggestedCategory().orElseThrow().getId());
        assertEquals(CategorySource.KEYWORD, loaded.getCategorySource());
        assertEquals(Impact.MEDIUM, loaded.getImpact());
        assertEquals(Urgency.HIGH, loaded.getUrgency());
        assertEquals(12, loaded.getAffectedUsers());
        assertEquals(PriorityLevel.P2, loaded.getPriority());
        assertEquals(22, loaded.getPriorityScore());
        assertEquals(T0, loaded.getCreatedAt());
        assertTrue(loaded.getAssignedTechnician().isEmpty());
        assertTrue(loaded.getResponseDeadline().isEmpty());
        assertEquals(Duration.ZERO, loaded.getPausedDuration());
        assertTrue(loaded.getNotes().isEmpty());
        assertTrue(loaded.getResolution().isEmpty());
        assertTrue(loaded.getFeedback().isEmpty());
        assertTrue(incidents.findById("INC9999").isEmpty());
    }

    @Test
    void incident_fullLifecycle_roundTripsAllFieldsAndKeepsWorkingStateMachine() {
        Incident inc = newIncident("INC1001", service1, "NETWORK", T0, new PriorityResult(18, PriorityLevel.P2));
        inc.applySlaDeadlines(T0.plusSeconds(900), T0.plusSeconds(7200));
        inc.classify();
        inc.assign(tech, T0.plusSeconds(60));
        inc.acknowledge(T0.plusSeconds(120));
        inc.startWork();
        inc.addInvestigationNote("T1", "checking logs", T0.plusSeconds(180));
        inc.requestInfo("T1", "which host?", T0.plusSeconds(200));
        inc.receiveInfo("E1", "host-1", T0.plusSeconds(800));
        inc.resolve("T1", "restarted service", T0.plusSeconds(900));
        inc.verify(T0.plusSeconds(1000));
        inc.close("A1", "done", T0.plusSeconds(1100));
        inc.submitFeedback("E1", 4, "thanks", T0.plusSeconds(1200));
        incidents.save(inc);
        flushAndClear();

        Incident loaded = incidents.findById("INC1001").orElseThrow();
        assertEquals(IncidentStatus.CLOSED, loaded.getStatus());
        assertEquals("T1", loaded.getAssignedTechnician().orElseThrow().getId());
        assertEquals(T0.plusSeconds(60), loaded.getAssignedAt().orElseThrow());
        assertEquals(T0.plusSeconds(120), loaded.getAcknowledgedAt().orElseThrow());
        assertEquals(T0.plusSeconds(900), loaded.getResponseDeadline().orElseThrow());
        assertEquals(T0.plusSeconds(7200), loaded.getResolutionDeadline().orElseThrow());
        assertEquals(Duration.ofSeconds(600), loaded.getPausedDuration());
        assertTrue(loaded.getWaitingSince().isEmpty());
        assertEquals("restarted service", loaded.getResolution().orElseThrow().summary());
        assertEquals("T1", loaded.getResolution().orElseThrow().resolverId());
        assertEquals(T0.plusSeconds(900), loaded.getResolvedAt().orElseThrow());
        assertEquals(T0.plusSeconds(1000), loaded.getVerifiedAt().orElseThrow());
        assertEquals(T0.plusSeconds(1100), loaded.getClosedAt().orElseThrow());
        assertEquals("A1", loaded.getClosedById().orElseThrow());
        assertEquals("done", loaded.getClosureNote().orElseThrow());
        assertEquals(4, loaded.getFeedback().orElseThrow().rating());
        assertEquals("thanks", loaded.getFeedback().orElseThrow().comment());
        assertEquals(List.of(NoteType.INVESTIGATION, NoteType.INFO_REQUEST, NoteType.INFO_RESPONSE),
                loaded.getNotes().stream().map(n -> n.type()).toList());
        assertEquals("checking logs", loaded.getNotes().get(0).text());
        // the restored aggregate still enforces the lifecycle: CLOSED is terminal
        assertThrows(com.iitsm.domain.exception.InvalidStateTransitionException.class, loaded::startWork);
    }

    @Test
    void incident_waitingForUser_restoresWaitingSinceAndContinuesWorkflow() {
        Incident inc = newIncident("INC1001", service1, "NETWORK", T0, new PriorityResult(18, PriorityLevel.P2));
        inc.applySlaDeadlines(T0.plusSeconds(900), T0.plusSeconds(7200));
        inc.classify();
        inc.assign(tech, T0.plusSeconds(60));
        inc.acknowledge(T0.plusSeconds(120));
        inc.startWork();
        inc.requestInfo("T1", "which host?", T0.plusSeconds(200));
        incidents.save(inc);
        flushAndClear();

        Incident loaded = incidents.findById("INC1001").orElseThrow();
        assertEquals(IncidentStatus.WAITING_FOR_USER, loaded.getStatus());
        assertEquals(T0.plusSeconds(200), loaded.getWaitingSince().orElseThrow());
        loaded.receiveInfo("E1", "host-1", T0.plusSeconds(500));
        assertEquals(IncidentStatus.IN_PROGRESS, loaded.getStatus());
        assertEquals(Duration.ofSeconds(300), loaded.getPausedDuration());
    }

    @Test
    void incident_notesAreAppendedAcrossSaves_inOrder() {
        Incident inc = newIncident("INC1001", service1, "NETWORK", T0, new PriorityResult(18, PriorityLevel.P2));
        inc.classify();
        inc.assign(tech, T0.plusSeconds(10));
        inc.acknowledge(T0.plusSeconds(20));
        incidents.save(inc);
        inc.addInvestigationNote("T1", "first", T0.plusSeconds(30));
        incidents.save(inc);
        inc.addAdditionalInfo("E1", "second", T0.plusSeconds(40));
        incidents.save(inc);
        incidents.save(inc);   // idempotent: no duplicate notes
        flushAndClear();

        Incident loaded = incidents.findById("INC1001").orElseThrow();
        assertEquals(List.of("first", "second"), loaded.getNotes().stream().map(n -> n.text()).toList());
        assertEquals(List.of(NoteType.INVESTIGATION, NoteType.ADDITIONAL_INFO),
                loaded.getNotes().stream().map(n -> n.type()).toList());
    }

    @Test
    void incident_reporterAndAssigneeQueries_filterByUser() {
        User other = users.save(TestData.employee("E2"));
        Incident a = newIncident("INC1001", service1, "NETWORK", T0, new PriorityResult(18, PriorityLevel.P2));
        a.classify();
        a.assign(tech, T0.plusSeconds(5));
        Incident b = new Incident("INC1002", TestData.draft(other, service1, TestData.category("DATABASE"),
                Impact.LOW, Urgency.LOW, 1), new PriorityResult(2, PriorityLevel.P4), T0.plusSeconds(60));
        incidents.save(a);
        incidents.save(b);
        flushAndClear();

        assertEquals(List.of("INC1001"), ids(incidents.findByReporterId("E1")));
        assertEquals(List.of("INC1002"), ids(incidents.findByReporterId("E2")));
        assertEquals(List.of("INC1001"), ids(incidents.findByAssignedTechnicianId("T1")));
        assertTrue(incidents.findByAssignedTechnicianId("T9").isEmpty());
        assertThrows(InvalidInputException.class, () -> incidents.findByReporterId(null));
    }

    @Test
    void incident_countActive_countsOnlyWorkloadStatusesOfThatTechnician() {
        Technician other = (Technician) users.save(TestData.technician("T2", 3));
        PriorityResult p = new PriorityResult(18, PriorityLevel.P2);

        Incident assigned = newIncident("INC1001", service1, "NETWORK", T0, p);
        assigned.classify();
        assigned.assign(tech, T0.plusSeconds(1));

        Incident inProgress = newIncident("INC1002", service1, "NETWORK", T0.plusSeconds(10), p);
        inProgress.classify();
        inProgress.assign(tech, T0.plusSeconds(11));
        inProgress.acknowledge(T0.plusSeconds(12));
        inProgress.startWork();

        Incident waiting = newIncident("INC1003", service1, "NETWORK", T0.plusSeconds(20), p);
        waiting.classify();
        waiting.assign(tech, T0.plusSeconds(21));
        waiting.acknowledge(T0.plusSeconds(22));
        waiting.startWork();
        waiting.requestInfo("T1", "details?", T0.plusSeconds(23));

        Incident resolved = newIncident("INC1004", service1, "NETWORK", T0.plusSeconds(30), p);
        resolved.classify();
        resolved.assign(tech, T0.plusSeconds(31));
        resolved.acknowledge(T0.plusSeconds(32));
        resolved.startWork();
        resolved.resolve("T1", "fixed", T0.plusSeconds(33));

        Incident unassigned = newIncident("INC1005", service1, "NETWORK", T0.plusSeconds(40), p);
        unassigned.classify();

        Incident otherTech = newIncident("INC1006", service1, "NETWORK", T0.plusSeconds(50), p);
        otherTech.classify();
        otherTech.assign(other, T0.plusSeconds(51));

        for (Incident i : List.of(assigned, inProgress, waiting, resolved, unassigned, otherTech)) {
            incidents.save(i);
        }
        flushAndClear();

        assertEquals(3, incidents.countActiveByTechnicianId("T1"));
        assertEquals(1, incidents.countActiveByTechnicianId("T2"));
        assertEquals(0, incidents.countActiveByTechnicianId("NOBODY"));
    }

    @Test
    void incident_search_appliesEveryCriterionWithAndSemantics() {
        ItService service2 = services.save(TestData.service("SVC2", BusinessCriticality.LOW));

        Incident one = newIncident("INC1001", service1, "NETWORK", T0, new PriorityResult(18, PriorityLevel.P2));
        one.classify();
        Incident two = newIncident("INC1002", service2, "DATABASE", T0.plusSeconds(3600),
                new PriorityResult(40, PriorityLevel.P1));
        two.classify();
        two.assign(tech, T0.plusSeconds(3700));
        Incident three = newIncident("INC1003", service1, "NETWORK", T0.plusSeconds(7200),
                new PriorityResult(40, PriorityLevel.P1));
        three.classify();
        three.assign(tech, T0.plusSeconds(7300));
        three.acknowledge(T0.plusSeconds(7310));
        three.startWork();
        incidents.save(three);   // saved out of order on purpose: results must still be ordered by creation
        incidents.save(one);
        incidents.save(two);
        flushAndClear();

        Instant h1 = T0.plusSeconds(3600);
        assertEquals(List.of("INC1001", "INC1002", "INC1003"), ids(incidents.search(IncidentSearchCriteria.none())));
        assertEquals(List.of("INC1001", "INC1002", "INC1003"), ids(incidents.findAll()));
        assertEquals(List.of("INC1002"), ids(incidents.search(crit("INC1002", null, null, null, null, null, null, null))));
        assertEquals(List.of("INC1002", "INC1003"), ids(incidents.search(crit(null, h1, null, null, null, null, null, null))));
        assertEquals(List.of("INC1001", "INC1002"), ids(incidents.search(crit(null, null, h1, null, null, null, null, null))));
        assertEquals(List.of("INC1002"), ids(incidents.search(crit(null, h1, h1, null, null, null, null, null))));
        assertEquals(List.of("INC1003"), ids(incidents.search(crit(null, null, null, IncidentStatus.IN_PROGRESS, null, null, null, null))));
        assertEquals(List.of("INC1002", "INC1003"), ids(incidents.search(crit(null, null, null, null, PriorityLevel.P1, null, null, null))));
        assertEquals(List.of("INC1001", "INC1003"), ids(incidents.search(crit(null, null, null, null, null, "NETWORK", null, null))));
        assertEquals(List.of("INC1002"), ids(incidents.search(crit(null, null, null, null, null, null, "SVC2", null))));
        assertEquals(List.of("INC1002", "INC1003"), ids(incidents.search(crit(null, null, null, null, null, null, null, "T1"))));
        // AND combinations
        assertEquals(List.of("INC1003"), ids(incidents.search(crit(null, null, null, null, PriorityLevel.P1, "NETWORK", null, null))));
        assertEquals(List.of("INC1002"), ids(incidents.search(crit(null, null, null, IncidentStatus.ASSIGNED, null, null, null, "T1"))));
        assertTrue(incidents.search(crit(null, null, null, null, null, null, null, "T9")).isEmpty());
        assertThrows(InvalidInputException.class, () -> incidents.search(null));
    }

    private static IncidentSearchCriteria crit(String id, Instant from, Instant to, IncidentStatus status,
                                               PriorityLevel priority, String categoryId, String serviceId,
                                               String technicianId) {
        return new IncidentSearchCriteria(id, from, to, status, priority, categoryId, serviceId, technicianId);
    }

    // ---------- audit ----------

    @Test
    void audit_saveAndRead_preservesFieldsOrderAndSystemActor() {
        audits.save(new AuditEntry(T0, "E1", "INC1001", null, IncidentStatus.NEW, "INCIDENT_CREATED", "created"));
        audits.save(new AuditEntry(T0.plusSeconds(1), "SYSTEM", "INC1001", IncidentStatus.NEW,
                IncidentStatus.CLASSIFIED, "INCIDENT_CLASSIFIED", "classified"));
        audits.save(new AuditEntry(T0.plusSeconds(2), "E2", "INC1002", null, IncidentStatus.NEW,
                "INCIDENT_CREATED", "created"));
        flushAndClear();

        List<AuditEntry> forIncident = audits.findByIncidentId("INC1001");
        assertEquals(2, forIncident.size());
        AuditEntry first = forIncident.get(0);
        assertEquals(T0, first.occurredAt());
        assertEquals("E1", first.actorId());
        assertNull(first.previousStatus());
        assertEquals(IncidentStatus.NEW, first.newStatus());
        assertEquals("INCIDENT_CREATED", first.action());
        assertEquals("created", first.description());
        AuditEntry second = forIncident.get(1);
        assertEquals("SYSTEM", second.actorId());
        assertEquals(IncidentStatus.NEW, second.previousStatus());
        assertEquals(IncidentStatus.CLASSIFIED, second.newStatus());
        assertEquals(List.of("INC1001", "INC1001", "INC1002"),
                audits.findAll().stream().map(AuditEntry::incidentId).toList());
        assertThrows(InvalidInputException.class, () -> audits.findByIncidentId(" "));
    }

    @Test
    void audit_nullNewStatus_isRejectedBecauseSchemaRequiresIt() {
        AuditEntry bad = new AuditEntry(T0, "SYSTEM", "INC1001", IncidentStatus.NEW, null, "X", "d");
        assertThrows(InvalidInputException.class, () -> audits.save(bad));
    }

    // ---------- notifications ----------

    private static Notification notification(String id, String recipient, String title) {
        return new Notification(id, recipient, title, "body of " + id, "INC1001", "IN_APP", false, T0);
    }

    @Test
    void notification_saveQueryMarkRead_persistsReadStateInInsertionOrder() {
        notifications.save(notification("N1", "E1", "one"));
        notifications.save(notification("N2", "T1", "two"));
        notifications.save(notification("N3", "E1", "three"));
        flushAndClear();

        assertEquals(List.of("N1", "N3"), notifications.findByRecipientId("E1").stream().map(Notification::id).toList());
        assertEquals(List.of("N1", "N3"), notifications.findUnreadByRecipientId("E1").stream().map(Notification::id).toList());
        Notification stored = notifications.findByRecipientId("T1").get(0);
        assertEquals("two", stored.title());
        assertEquals("body of N2", stored.body());
        assertEquals("INC1001", stored.incidentId());
        assertEquals("IN_APP", stored.channelName());
        assertEquals(T0, stored.createdAt());
        assertFalse(stored.read());

        notifications.markRead("N1");
        flushAndClear();
        assertEquals(List.of("N3"), notifications.findUnreadByRecipientId("E1").stream().map(Notification::id).toList());
        assertTrue(notifications.findByRecipientId("E1").get(0).read());
        notifications.markRead("does-not-exist");   // no-op, like the in-memory implementation
        assertThrows(InvalidInputException.class, () -> notifications.markRead(" "));
        assertThrows(InvalidInputException.class, () -> notifications.findByRecipientId(null));
    }

    @Test
    void notification_saveSameIdTwice_updatesInPlaceAndKeepsOrder() {
        notifications.save(notification("N1", "E1", "one"));
        notifications.save(notification("N2", "E1", "two"));
        flushAndClear();
        notifications.save(notification("N1", "E1", "one-updated").withRead(true));
        flushAndClear();

        List<Notification> all = notifications.findAll();
        assertEquals(List.of("N1", "N2"), all.stream().map(Notification::id).toList());
        assertEquals("one-updated", all.get(0).title());
        assertTrue(all.get(0).read());
    }
}
