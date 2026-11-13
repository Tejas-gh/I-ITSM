package com.iitsm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.iitsm.api.dto.AssignIncidentRequest;
import com.iitsm.api.dto.CategorySuggestionResponse;
import com.iitsm.api.dto.CloseIncidentRequest;
import com.iitsm.api.dto.CreateIncidentRequest;
import com.iitsm.api.dto.IncidentResponse;
import com.iitsm.api.dto.IncidentTextRequest;
import com.iitsm.api.dto.SubmitFeedbackRequest;
import com.iitsm.api.dto.SuggestCategoryRequest;
import com.iitsm.api.dto.UpdatePriorityInputsRequest;
import com.iitsm.application.service.IncidentSearchService;
import com.iitsm.application.service.IncidentService;
import com.iitsm.application.service.ServiceFixture;
import com.iitsm.domain.classification.CategorySuggestion;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.sla.SlaEvaluator;

/**
 * The controller must use the authenticated principal as the actor and only translate DTOs to commands.
 * Driven with the real application services on in-memory repositories (see ServiceFixture).
 */
class IncidentControllerTest {

    private ServiceFixture f;
    private IncidentController controller;

    private static Authentication as(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, "n/a");
    }

    private final Authentication employee = as("EMP01");
    private final Authentication tech = as("TECH01");
    private final Authentication admin = as("ADMIN01");

    @BeforeEach
    void setUp() {
        f = new ServiceFixture();
        controller = new IncidentController(f.service,
                new IncidentSearchService(f.incidents, f.users, new SlaEvaluator(), f.clock));
    }

    private static CreateIncidentRequest createRequest() {
        return new CreateIncidentRequest("Payment app down", "Users cannot pay", "SRV-PAYMENT", "APPLICATION",
                null, CategorySource.MANUAL, Impact.HIGH, Urgency.HIGH, 12);
    }

    private IncidentResponse created() {
        return controller.create(createRequest(), employee);
    }

    /** Leaves the new incident CLASSIFIED (no assignable technician), so assignment endpoints can be exercised. */
    private IncidentResponse createdUnassigned() {
        f.technician.markUnavailable();
        f.technician2.markUnavailable();
        IncidentResponse r = created();
        f.technician.markAvailable();
        f.technician2.markAvailable();
        return r;
    }

    // ---------- create ----------

    @Test
    void create_usesThePrincipalAsReporterAndMapsEveryField() {
        IncidentResponse r = created();

        assertEquals("EMP01", r.reporterId());
        assertEquals("Payment app down", r.title());
        assertEquals("Users cannot pay", r.description());
        assertEquals("SRV-PAYMENT", r.serviceId());
        assertEquals("APPLICATION", r.categoryId());
        assertEquals(CategorySource.MANUAL, r.categorySource());
        assertEquals(Impact.HIGH, r.impact());
        assertEquals(Urgency.HIGH, r.urgency());
        assertEquals(12, r.affectedUsers());
        assertEquals(PriorityLevel.P1, r.priority());   // 3 x 3 x 4 = 36
        assertEquals(40, r.priorityScore());            // + 4 for 10..49 affected users
        assertEquals(IncidentStatus.ASSIGNED, r.status());
        assertEquals("TECH01", r.assignedTechnicianId());
        assertNotNull(r.responseDeadline());
        assertNotNull(r.resolutionDeadline());
    }

    @Test
    void create_actorComesFromThePrincipal_notFromTheBody() {
        assertThrows(UnauthorizedOperationException.class, () -> controller.create(createRequest(), admin));
        assertThrows(UnauthorizedOperationException.class, () -> controller.create(createRequest(), tech));
    }

    @Test
    void create_missingCategorySource_isInvalidInput() {
        CreateIncidentRequest noSource = new CreateIncidentRequest("t", "d", "SRV-PAYMENT", "APPLICATION", null,
                null, Impact.HIGH, Urgency.HIGH, 1);
        assertThrows(InvalidInputException.class, () -> controller.create(noSource, employee));
    }

    // ---------- assignment ----------

    @Test
    void assign_adminAssignsClassifiedIncident_thenReassignMovesIt() {
        IncidentResponse c = createdUnassigned();
        assertEquals(IncidentStatus.CLASSIFIED, c.status());

        IncidentResponse assigned = controller.assign(c.id(), new AssignIncidentRequest("TECH02"), admin);
        assertEquals(IncidentStatus.ASSIGNED, assigned.status());
        assertEquals("TECH02", assigned.assignedTechnicianId());

        IncidentResponse moved = controller.reassign(c.id(), new AssignIncidentRequest("TECH01"), admin);
        assertEquals("TECH01", moved.assignedTechnicianId());
        assertEquals(IncidentStatus.ASSIGNED, moved.status());
    }

    @Test
    void autoAssign_assignsWhenATechnicianBecomesAvailable() {
        IncidentResponse c = createdUnassigned();

        IncidentResponse r = controller.autoAssign(c.id(), admin);

        assertEquals(IncidentStatus.ASSIGNED, r.status());
        assertEquals("TECH01", r.assignedTechnicianId());
        assertThrows(UnauthorizedOperationException.class, () -> controller.autoAssign(c.id(), employee));
    }

    // ---------- lifecycle through the controller ----------

    @Test
    void lifecycle_everyOperation_isRoutedWithThePrincipalAsActor() {
        String id = created().id();

        assertEquals(IncidentStatus.ACKNOWLEDGED, controller.acknowledge(id, tech).status());
        assertEquals(IncidentStatus.IN_PROGRESS, controller.startWork(id, tech).status());
        assertEquals(1, controller.addNote(id, new IncidentTextRequest("checking"), tech).notes().size());
        assertEquals(IncidentStatus.WAITING_FOR_USER,
                controller.requestInfo(id, new IncidentTextRequest("which host?"), tech).status());
        assertEquals(IncidentStatus.IN_PROGRESS,
                controller.respondInfo(id, new IncidentTextRequest("host-1"), employee).status());
        // investigation + info request + info response + additional info
        assertEquals(4, controller.additionalInfo(id, new IncidentTextRequest("since 9am"), employee).notes().size());
        assertEquals(IncidentStatus.RESOLVED,
                controller.resolve(id, new IncidentTextRequest("restarted"), tech).status());
        assertEquals(IncidentStatus.VERIFIED, controller.verify(id, employee).status());
        IncidentResponse closed = controller.close(id, new CloseIncidentRequest("confirmed"), admin);
        assertEquals(IncidentStatus.CLOSED, closed.status());
        assertNotNull(closed.closedAt());
        IncidentResponse withFeedback = controller.feedback(id, new SubmitFeedbackRequest(5, "great"), employee);
        assertEquals(5, withFeedback.feedback().rating());
        assertEquals("great", withFeedback.feedback().comment());
        assertEquals("restarted", controller.get(id, employee).resolution().summary());
    }

    @Test
    void rejectResolution_reporterReopensTheIncident() {
        String id = created().id();
        controller.acknowledge(id, tech);
        controller.startWork(id, tech);
        controller.resolve(id, new IncidentTextRequest("tried"), tech);

        IncidentResponse r = controller.rejectResolution(id, employee);

        assertEquals(IncidentStatus.IN_PROGRESS, r.status());
        assertNull(r.resolution());
    }

    @Test
    void updatePriorityInputs_adminRecalculatesPriority() {
        String id = created().id();

        IncidentResponse r = controller.updatePriorityInputs(id,
                new UpdatePriorityInputsRequest(Impact.LOW, Urgency.LOW), admin);

        assertEquals(Impact.LOW, r.impact());
        assertEquals(Urgency.LOW, r.urgency());
        assertEquals(PriorityLevel.P3, r.priority());   // 1 x 1 x 4 (critical service) + 4 = 8
        assertEquals(8, r.priorityScore());
        assertThrows(UnauthorizedOperationException.class, () -> controller.updatePriorityInputs(id,
                new UpdatePriorityInputsRequest(Impact.HIGH, Urgency.HIGH), employee));
    }

    @Test
    void get_followsVisibilityRules() {
        String id = created().id();

        assertEquals(id, controller.get(id, employee).id());
        assertEquals(id, controller.get(id, admin).id());
        assertEquals(id, controller.get(id, tech).id());
        assertThrows(UnauthorizedOperationException.class, () -> controller.get(id, as("TECH02")));
    }

    // ---------- suggest category ----------

    @Test
    void suggestCategory_noSuggestion_returns200WithEmptyBody() {
        ResponseEntity<CategorySuggestionResponse> r =
                controller.suggestCategory(new SuggestCategoryRequest("wifi", "down"), employee);

        assertEquals(200, r.getStatusCode().value());
        assertNull(r.getBody());
    }

    @Test
    void suggestCategory_withSuggestion_returns200WithBody() {
        IncidentService withClassifier = new IncidentService(f.incidents, f.users, f.services, f.categories,
                f.incidentFactory, f.priorityStrategy, f.slaCalculator, f.assignmentStrategy,
                (title, description) -> Optional.of(
                        new CategorySuggestion("NETWORK", 0.9, "keyword match", CategorySource.KEYWORD)),
                f.permissions, f.events, f.clock);
        IncidentController c = new IncidentController(withClassifier,
                new IncidentSearchService(f.incidents, f.users, new SlaEvaluator(), f.clock));

        ResponseEntity<CategorySuggestionResponse> r = c.suggestCategory(
                new SuggestCategoryRequest("wifi", "down"), employee);

        assertEquals(200, r.getStatusCode().value());
        assertEquals("NETWORK", r.getBody().categoryId());
        assertEquals(0.9, r.getBody().confidence());
        assertEquals(CategorySource.KEYWORD, r.getBody().source());
    }

    // ---------- list / search ----------

    private static final List<String> PARAMS = List.of("incidentId", "createdFrom", "createdTo", "status",
            "priority", "categoryId", "serviceId", "technicianId", "slaStatus");

    /** Calls the list endpoint with only the given query parameters (name, value, name, value, ...). */
    private List<IncidentResponse> filtered(Authentication who, String... nameValuePairs) {
        String[] a = new String[PARAMS.size()];
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            a[PARAMS.indexOf(nameValuePairs[i])] = nameValuePairs[i + 1];
        }
        return controller.list(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], who);
    }

    @Test
    void list_noParameters_returnsTheCallersVisibleIncidents() {
        String mine = created().id();

        assertEquals(List.of(mine), filtered(employee).stream().map(IncidentResponse::id).toList());
        assertEquals(List.of(), filtered(as("TECH02")));
        assertEquals(1, filtered(admin).size());
    }

    @Test
    void list_everyFilter_isParsedAndApplied() {
        String id = created().id();
        Instant now = f.clock.instant();

        assertEquals(1, filtered(admin, "incidentId", id).size());
        assertEquals(0, filtered(admin, "incidentId", "INC0").size());
        assertEquals(1, filtered(admin, "createdFrom", now.minusSeconds(60).toString(),
                "createdTo", now.plusSeconds(60).toString()).size());
        assertEquals(0, filtered(admin, "createdFrom", now.plusSeconds(60).toString()).size());
        assertEquals(0, filtered(admin, "createdTo", now.minusSeconds(60).toString()).size());
        assertEquals(1, filtered(admin, "status", "ASSIGNED").size());
        assertEquals(0, filtered(admin, "status", "RESOLVED").size());
        assertEquals(1, filtered(admin, "priority", "P1").size());
        assertEquals(0, filtered(admin, "priority", "P4").size());
        assertEquals(1, filtered(admin, "categoryId", "APPLICATION").size());
        assertEquals(0, filtered(admin, "categoryId", "NETWORK").size());
        assertEquals(1, filtered(admin, "serviceId", "SRV-PAYMENT").size());
        assertEquals(1, filtered(admin, "technicianId", "TECH01").size());
        assertEquals(0, filtered(admin, "technicianId", "TECH02").size());
        assertEquals(1, filtered(admin, "status", "ASSIGNED", "priority", "P1", "technicianId", "TECH01").size());
        assertEquals(0, filtered(admin, "status", "ASSIGNED", "priority", "P4").size());
        assertEquals(id, filtered(admin, "incidentId", id).get(0).id());
    }

    @Test
    void list_slaStatusFilter_usesTheCurrentTime() {
        created();

        assertEquals(1, filtered(admin, "slaStatus", "ON_TRACK").size());
        assertEquals(0, filtered(admin, "slaStatus", "BREACHED").size());

        f.clock.advance(Duration.ofHours(10));

        assertEquals(0, filtered(admin, "slaStatus", "ON_TRACK").size());
        assertEquals(1, filtered(admin, "slaStatus", "BREACHED").size());
    }

    @Test
    void list_invalidEnumOrInstant_isInvalidInput() {
        assertThrows(InvalidInputException.class, () -> filtered(admin, "status", "NOPE"));
        assertThrows(InvalidInputException.class, () -> filtered(admin, "priority", "P9"));
        assertThrows(InvalidInputException.class, () -> filtered(admin, "slaStatus", "NOPE"));
        assertThrows(InvalidInputException.class, () -> filtered(admin, "createdFrom", "yesterday"));
        assertThrows(InvalidInputException.class, () -> filtered(admin, "createdTo", "2026-11-16"));
        assertTrue(filtered(admin).isEmpty());
    }
}
