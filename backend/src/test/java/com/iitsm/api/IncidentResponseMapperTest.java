package com.iitsm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.iitsm.api.dto.CategorySuggestionResponse;
import com.iitsm.api.dto.IncidentResponse;
import com.iitsm.api.dto.IncidentResponseMapper;
import com.iitsm.domain.classification.CategorySuggestion;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.NoteType;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.priority.PriorityResult;
import com.iitsm.support.TestData;

class IncidentResponseMapperTest {

    private static final Instant T0 = Instant.parse("2026-11-16T09:00:00Z");

    private Incident newIncident() {
        return new Incident("INC1001",
                TestData.draft(TestData.employee("E1"), TestData.service("SVC1", BusinessCriticality.HIGH),
                        TestData.category("NETWORK"), Impact.HIGH, Urgency.HIGH, 5),
                new PriorityResult(27, PriorityLevel.P2), T0);
    }

    @Test
    void incidentResponse_fields_areExactlyTheContractList() {
        List<String> names = Arrays.stream(IncidentResponse.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertEquals(List.of("id", "title", "description", "reporterId", "serviceId", "categoryId",
                "suggestedCategoryId", "categorySource", "impact", "urgency", "affectedUsers", "priority",
                "priorityScore", "status", "assignedTechnicianId", "createdAt", "responseDeadline",
                "resolutionDeadline", "acknowledgedAt", "resolvedAt", "verifiedAt", "closedAt", "notes",
                "resolution", "feedback"), names);
    }

    @Test
    void toResponse_newIncident_mapsFieldsAndLeavesAbsentValuesNull() {
        IncidentResponse r = IncidentResponseMapper.toResponse(newIncident());

        assertEquals("INC1001", r.id());
        assertEquals("E1", r.reporterId());
        assertEquals("SVC1", r.serviceId());
        assertEquals("NETWORK", r.categoryId());
        assertNull(r.suggestedCategoryId());
        assertEquals(CategorySource.MANUAL, r.categorySource());
        assertEquals(Impact.HIGH, r.impact());
        assertEquals(Urgency.HIGH, r.urgency());
        assertEquals(5, r.affectedUsers());
        assertEquals(PriorityLevel.P2, r.priority());
        assertEquals(27, r.priorityScore());
        assertEquals(IncidentStatus.NEW, r.status());
        assertEquals(T0, r.createdAt());
        assertNull(r.assignedTechnicianId());
        assertNull(r.responseDeadline());
        assertNull(r.acknowledgedAt());
        assertNull(r.resolvedAt());
        assertNull(r.verifiedAt());
        assertNull(r.closedAt());
        assertEquals(List.of(), r.notes());
        assertNull(r.resolution());
        assertNull(r.feedback());
    }

    @Test
    void toResponse_closedIncident_mapsLifecycleDataNotesResolutionAndFeedback() {
        Technician tech = TestData.technician("T1", 3);
        Incident inc = newIncident();
        inc.applySlaDeadlines(T0.plusSeconds(900), T0.plusSeconds(7200));
        inc.classify();
        inc.assign(tech, T0.plusSeconds(60));
        inc.acknowledge(T0.plusSeconds(120));
        inc.startWork();
        inc.addInvestigationNote("T1", "checking logs", T0.plusSeconds(180));
        inc.resolve("T1", "restarted service", T0.plusSeconds(900));
        inc.verify(T0.plusSeconds(1000));
        inc.close("A1", "done", T0.plusSeconds(1100));
        inc.submitFeedback("E1", 4, "thanks", T0.plusSeconds(1200));

        IncidentResponse r = IncidentResponseMapper.toResponse(inc);

        assertEquals(IncidentStatus.CLOSED, r.status());
        assertEquals("T1", r.assignedTechnicianId());
        assertEquals(T0.plusSeconds(900), r.responseDeadline());
        assertEquals(T0.plusSeconds(7200), r.resolutionDeadline());
        assertEquals(T0.plusSeconds(120), r.acknowledgedAt());
        assertEquals(T0.plusSeconds(900), r.resolvedAt());
        assertEquals(T0.plusSeconds(1000), r.verifiedAt());
        assertEquals(T0.plusSeconds(1100), r.closedAt());
        assertEquals(1, r.notes().size());
        assertEquals(NoteType.INVESTIGATION, r.notes().get(0).type());
        assertEquals("checking logs", r.notes().get(0).text());
        assertEquals("T1", r.notes().get(0).authorId());
        assertEquals("restarted service", r.resolution().summary());
        assertEquals("T1", r.resolution().resolverId());
        assertEquals(4, r.feedback().rating());
        assertEquals("thanks", r.feedback().comment());
        assertEquals("E1", r.feedback().authorId());
    }

    @Test
    void toResponse_categorySuggestion_mapsAllFields() {
        CategorySuggestionResponse r = IncidentResponseMapper.toResponse(
                new CategorySuggestion("NETWORK", 0.8, "keyword match", CategorySource.KEYWORD));
        assertEquals("NETWORK", r.categoryId());
        assertEquals(0.8, r.confidence());
        assertEquals("keyword match", r.reason());
        assertEquals(CategorySource.KEYWORD, r.source());
        assertNotNull(IncidentResponseMapper.toResponses(List.of(newIncident())).get(0));
    }
}
