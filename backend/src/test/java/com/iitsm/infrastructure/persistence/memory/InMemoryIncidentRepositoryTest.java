package com.iitsm.infrastructure.persistence.memory;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentDraft;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.priority.PriorityResult;
import com.iitsm.domain.repository.IncidentSearchCriteria;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryIncidentRepositoryTest {

    private InMemoryIncidentRepository repository;
    private Employee reporter1;
    private Employee reporter2;
    private Technician tech1;
    private Technician tech2;
    private ItService service1;
    private ItService service2;
    private Category cat1;
    private Category cat2;

    private static final Instant T0 = Instant.parse("2026-11-16T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-16T11:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-16T12:00:00Z");
    private static final Instant T3 = Instant.parse("2026-11-16T13:00:00Z");

    @BeforeEach
    void setUp() {
        repository = new InMemoryIncidentRepository();
        reporter1 = TestData.employee("EMP1");
        reporter2 = TestData.employee("EMP2");
        tech1 = TestData.technician("TECH1", 5);
        tech2 = TestData.technician("TECH2", 5);
        service1 = TestData.service("SVC1", BusinessCriticality.HIGH);
        service2 = TestData.service("SVC2", BusinessCriticality.LOW);
        cat1 = TestData.category("NETWORK");
        cat2 = TestData.category("DATABASE");
    }

    private Incident createIncident(String id, Employee reporter, ItService service, Category cat,
                                    PriorityLevel level, int score, Instant createdAt) {
        IncidentDraft draft = TestData.draft(reporter, service, cat, Impact.HIGH, Urgency.HIGH, 1);
        return new Incident(id, draft, new PriorityResult(score, level), createdAt);
    }

    @Test
    void save_newIncident_storesAndReturnsSameReference() {
        Incident inc = createIncident("INC1001", reporter1, service1, cat1, PriorityLevel.P2, 27, T0);
        Incident saved = repository.save(inc);

        assertSame(inc, saved);
        Optional<Incident> found = repository.findById("INC1001");
        assertTrue(found.isPresent());
        assertSame(inc, found.get());
    }

    @Test
    void save_existingId_upsertsKeyedById() {
        Incident inc1 = createIncident("INC1001", reporter1, service1, cat1, PriorityLevel.P2, 27, T0);
        repository.save(inc1);

        Incident inc2 = createIncident("INC1001", reporter2, service2, cat2, PriorityLevel.P1, 64, T1);
        repository.save(inc2);

        assertEquals(1, repository.findAll().size());
        assertSame(inc2, repository.findById("INC1001").orElseThrow());
    }

    @Test
    void findById_nonExistent_returnsEmptyOptional() {
        assertTrue(repository.findById("NON_EXISTENT").isEmpty());
    }

    @Test
    void findAll_multipleIncidents_preservesInsertionOrder() {
        Incident inc1 = createIncident("INC1001", reporter1, service1, cat1, PriorityLevel.P1, 36, T0);
        Incident inc2 = createIncident("INC1002", reporter2, service2, cat2, PriorityLevel.P2, 18, T1);
        Incident inc3 = createIncident("INC1003", reporter1, service2, cat1, PriorityLevel.P3, 8, T2);

        repository.save(inc1);
        repository.save(inc2);
        repository.save(inc3);

        List<Incident> all = repository.findAll();
        assertEquals(3, all.size());
        assertEquals("INC1001", all.get(0).getId());
        assertEquals("INC1002", all.get(1).getId());
        assertEquals("INC1003", all.get(2).getId());
    }

    @Test
    void findByReporterId_filtersCorrectlyInInsertionOrder() {
        Incident inc1 = createIncident("INC1001", reporter1, service1, cat1, PriorityLevel.P1, 36, T0);
        Incident inc2 = createIncident("INC1002", reporter2, service2, cat2, PriorityLevel.P2, 18, T1);
        Incident inc3 = createIncident("INC1003", reporter1, service2, cat1, PriorityLevel.P3, 8, T2);

        repository.save(inc1);
        repository.save(inc2);
        repository.save(inc3);

        List<Incident> reporter1Incidents = repository.findByReporterId("EMP1");
        assertEquals(2, reporter1Incidents.size());
        assertEquals("INC1001", reporter1Incidents.get(0).getId());
        assertEquals("INC1003", reporter1Incidents.get(1).getId());

        List<Incident> reporter2Incidents = repository.findByReporterId("EMP2");
        assertEquals(1, reporter2Incidents.size());
        assertEquals("INC1002", reporter2Incidents.get(0).getId());

        List<Incident> emptyList = repository.findByReporterId("EMP_UNKNOWN");
        assertTrue(emptyList.isEmpty());
    }

    @Test
    void findByAssignedTechnicianId_filtersAssignedIncidents() {
        Incident inc1 = createIncident("INC1001", reporter1, service1, cat1, PriorityLevel.P1, 36, T0);
        inc1.classify();
        inc1.assign(tech1, T0);

        Incident inc2 = createIncident("INC1002", reporter2, service2, cat2, PriorityLevel.P2, 18, T1);
        inc2.classify();
        inc2.assign(tech2, T1);

        Incident inc3 = createIncident("INC1003", reporter1, service1, cat1, PriorityLevel.P3, 8, T2);
        // inc3 unassigned (NEW)

        repository.save(inc1);
        repository.save(inc2);
        repository.save(inc3);

        List<Incident> tech1Incidents = repository.findByAssignedTechnicianId("TECH1");
        assertEquals(1, tech1Incidents.size());
        assertEquals("INC1001", tech1Incidents.get(0).getId());

        List<Incident> tech2Incidents = repository.findByAssignedTechnicianId("TECH2");
        assertEquals(1, tech2Incidents.size());
        assertEquals("INC1002", tech2Incidents.get(0).getId());

        assertTrue(repository.findByAssignedTechnicianId("TECH_UNKNOWN").isEmpty());
    }

    @Test
    void countActiveByTechnicianId_countsOnlyWorkloadStatuses() {
        // ASSIGNED counts
        Incident incAssigned = createIncident("INC1", reporter1, service1, cat1, PriorityLevel.P2, 18, T0);
        incAssigned.classify();
        incAssigned.assign(tech1, T0);

        // ACKNOWLEDGED counts
        Incident incAck = createIncident("INC2", reporter1, service1, cat1, PriorityLevel.P2, 18, T0);
        incAck.classify();
        incAck.assign(tech1, T0);
        incAck.acknowledge(T1);

        // IN_PROGRESS counts
        Incident incProgress = createIncident("INC3", reporter1, service1, cat1, PriorityLevel.P2, 18, T0);
        incProgress.classify();
        incProgress.assign(tech1, T0);
        incProgress.acknowledge(T1);
        incProgress.startWork();

        // WAITING_FOR_USER counts
        Incident incWaiting = createIncident("INC4", reporter1, service1, cat1, PriorityLevel.P2, 18, T0);
        incWaiting.classify();
        incWaiting.assign(tech1, T0);
        incWaiting.acknowledge(T1);
        incWaiting.startWork();
        incWaiting.requestInfo("TECH1", "Need info", T2);

        // RESOLVED does NOT count
        Incident incResolved = createIncident("INC5", reporter1, service1, cat1, PriorityLevel.P2, 18, T0);
        incResolved.classify();
        incResolved.assign(tech1, T0);
        incResolved.acknowledge(T1);
        incResolved.startWork();
        incResolved.resolve("TECH1", "Fixed it", T2);

        repository.save(incAssigned);
        repository.save(incAck);
        repository.save(incProgress);
        repository.save(incWaiting);
        repository.save(incResolved);

        // Should count only the 4 workload-eligible statuses
        assertEquals(4, repository.countActiveByTechnicianId("TECH1"));
        assertEquals(0, repository.countActiveByTechnicianId("TECH2"));
    }

    @Test
    void search_eachOfSevenCriteria_filtersCorrectly() {
        Incident inc1 = createIncident("INC1001", reporter1, service1, cat1, PriorityLevel.P1, 36, T1);
        inc1.classify();
        inc1.assign(tech1, T1);

        Incident inc2 = createIncident("INC1002", reporter2, service2, cat2, PriorityLevel.P2, 18, T2);
        inc2.classify();
        inc2.assign(tech2, T2);
        inc2.acknowledge(T2);

        repository.save(inc1);
        repository.save(inc2);

        // 1. by incidentId
        assertEquals(1, repository.search(new IncidentSearchCriteria("INC1001", null, null, null, null, null, null, null)).size());
        assertEquals("INC1001", repository.search(new IncidentSearchCriteria("INC1001", null, null, null, null, null, null, null)).get(0).getId());

        // 2. by date bounds
        assertEquals(2, repository.search(new IncidentSearchCriteria(null, T1, T2, null, null, null, null, null)).size());
        assertEquals(1, repository.search(new IncidentSearchCriteria(null, T2, T3, null, null, null, null, null)).size());
        assertEquals(0, repository.search(new IncidentSearchCriteria(null, T3, null, null, null, null, null, null)).size());

        // 3. by status
        assertEquals(1, repository.search(new IncidentSearchCriteria(null, null, null, IncidentStatus.ASSIGNED, null, null, null, null)).size());
        assertEquals(1, repository.search(new IncidentSearchCriteria(null, null, null, IncidentStatus.ACKNOWLEDGED, null, null, null, null)).size());
        assertEquals(0, repository.search(new IncidentSearchCriteria(null, null, null, IncidentStatus.RESOLVED, null, null, null, null)).size());

        // 4. by priority
        assertEquals(1, repository.search(new IncidentSearchCriteria(null, null, null, null, PriorityLevel.P1, null, null, null)).size());
        assertEquals(1, repository.search(new IncidentSearchCriteria(null, null, null, null, PriorityLevel.P2, null, null, null)).size());
        assertEquals(0, repository.search(new IncidentSearchCriteria(null, null, null, null, PriorityLevel.P4, null, null, null)).size());

        // 5. by categoryId
        assertEquals(1, repository.search(new IncidentSearchCriteria(null, null, null, null, null, "NETWORK", null, null)).size());
        assertEquals(1, repository.search(new IncidentSearchCriteria(null, null, null, null, null, "DATABASE", null, null)).size());

        // 6. by serviceId
        assertEquals(1, repository.search(new IncidentSearchCriteria(null, null, null, null, null, null, "SVC1", null)).size());
        assertEquals(1, repository.search(new IncidentSearchCriteria(null, null, null, null, null, null, "SVC2", null)).size());

        // 7. by technicianId
        assertEquals(1, repository.search(new IncidentSearchCriteria(null, null, null, null, null, null, null, "TECH1")).size());
        assertEquals(1, repository.search(new IncidentSearchCriteria(null, null, null, null, null, null, null, "TECH2")).size());
        assertEquals(0, repository.search(new IncidentSearchCriteria(null, null, null, null, null, null, null, "TECH_UNKNOWN")).size());
    }

    @Test
    void search_combinedCriteria_appliesAndLogic() {
        Incident inc1 = createIncident("INC1001", reporter1, service1, cat1, PriorityLevel.P1, 36, T1);
        Incident inc2 = createIncident("INC1002", reporter2, service1, cat2, PriorityLevel.P1, 36, T2);

        repository.save(inc1);
        repository.save(inc2);

        // Both are P1 and SVC1, but different categories
        IncidentSearchCriteria criteria1 = new IncidentSearchCriteria(null, null, null, null, PriorityLevel.P1, "NETWORK", "SVC1", null);
        List<Incident> result1 = repository.search(criteria1);
        assertEquals(1, result1.size());
        assertEquals("INC1001", result1.get(0).getId());

        IncidentSearchCriteria criteriaMismatch = new IncidentSearchCriteria(null, null, null, null, PriorityLevel.P2, "NETWORK", "SVC1", null);
        List<Incident> resultMismatch = repository.search(criteriaMismatch);
        assertTrue(resultMismatch.isEmpty());
    }

    @Test
    void search_none_returnsAllInInsertionOrder() {
        Incident inc1 = createIncident("INC1001", reporter1, service1, cat1, PriorityLevel.P1, 36, T0);
        Incident inc2 = createIncident("INC1002", reporter2, service2, cat2, PriorityLevel.P2, 18, T1);

        repository.save(inc1);
        repository.save(inc2);

        List<Incident> all = repository.search(IncidentSearchCriteria.none());
        assertEquals(2, all.size());
        assertEquals("INC1001", all.get(0).getId());
        assertEquals("INC1002", all.get(1).getId());
    }

    @Test
    void nullArguments_throwInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> repository.save(null));
        assertThrows(InvalidInputException.class, () -> repository.findById(null));
        assertThrows(InvalidInputException.class, () -> repository.findByReporterId(null));
        assertThrows(InvalidInputException.class, () -> repository.findByAssignedTechnicianId(null));
        assertThrows(InvalidInputException.class, () -> repository.countActiveByTechnicianId(null));
        assertThrows(InvalidInputException.class, () -> repository.search(null));
    }

    @Test
    void returnedLists_areUnmodifiable() {
        Incident inc = createIncident("INC1001", reporter1, service1, cat1, PriorityLevel.P1, 36, T0);
        repository.save(inc);

        List<Incident> all = repository.findAll();
        assertThrows(UnsupportedOperationException.class, () -> all.add(inc));

        List<Incident> byReporter = repository.findByReporterId("EMP1");
        assertThrows(UnsupportedOperationException.class, () -> byReporter.add(inc));

        List<Incident> searched = repository.search(IncidentSearchCriteria.none());
        assertThrows(UnsupportedOperationException.class, () -> searched.add(inc));
    }
}

