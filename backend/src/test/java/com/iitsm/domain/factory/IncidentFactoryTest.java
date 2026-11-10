package com.iitsm.domain.factory;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentDraft;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.model.User;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.priority.WeightedPriorityStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class IncidentFactoryTest {

    // --- stub id generator ---
    private static class StubIdGenerator implements IncidentIdGenerator {
        private final AtomicInteger counter = new AtomicInteger(1001);
        @Override
        public String nextIncidentId() {
            return "INC" + counter.getAndIncrement();
        }
    }

    private StubIdGenerator idGen;
    private WeightedPriorityStrategy strategy;
    private IncidentFactory factory;

    private Employee reporter;
    private ItService service;
    private Category category;

    private static final Instant CREATED_AT = Instant.parse("2026-11-16T09:00:00Z");

    @BeforeEach
    void setUp() {
        idGen = new StubIdGenerator();
        strategy = new WeightedPriorityStrategy();
        factory = new IncidentFactory(idGen, strategy);

        reporter = new Employee("U1", "Alice", "alice@corp.com", "IT");
        service  = new ItService("SVC1", "Network", BusinessCriticality.HIGH);
        category = new Category("NETWORK", "Network Issues");
    }

    private IncidentDraft buildDraft(Impact impact, Urgency urgency, int affectedUsers) {
        return new IncidentDraft(
                "Cannot connect to VPN",
                "VPN login fails for all users.",
                reporter, service, category,
                null, CategorySource.MANUAL,
                impact, urgency, affectedUsers
        );
    }

    @Test
    void create_validDraft_assignsIdFromGenerator() {
        IncidentDraft draft = buildDraft(Impact.MEDIUM, Urgency.MEDIUM, 1);

        Incident inc = factory.create(draft, CREATED_AT);

        assertEquals("INC1001", inc.getId());

        Incident inc2 = factory.create(draft, CREATED_AT);
        assertEquals("INC1002", inc2.getId());
    }

    @Test
    void create_validDraft_appliesPriorityFromStrategy() {
        // HIGH / HIGH / HIGH(service) / 1 = 3*3*3+0 = 27 → P2
        IncidentDraft draft = buildDraft(Impact.HIGH, Urgency.HIGH, 1);

        Incident inc = factory.create(draft, CREATED_AT);

        assertEquals(27, inc.getPriorityScore());
        assertEquals(com.iitsm.domain.model.PriorityLevel.P2, inc.getPriority());
    }

    @Test
    void create_validDraft_statusIsNew() {
        IncidentDraft draft = buildDraft(Impact.LOW, Urgency.LOW, 1);

        Incident inc = factory.create(draft, CREATED_AT);

        assertEquals(IncidentStatus.NEW, inc.getStatus());
    }

    @Test
    void create_validDraft_createdAtIsSet() {
        IncidentDraft draft = buildDraft(Impact.LOW, Urgency.LOW, 1);

        Incident inc = factory.create(draft, CREATED_AT);

        assertEquals(CREATED_AT, inc.getCreatedAt());
    }

    @Test
    void create_nullDraft_throwsInvalidInput() {
        assertThrows(InvalidInputException.class, () -> factory.create(null, CREATED_AT));
    }

    @Test
    void create_nullCreatedAt_throwsInvalidInput() {
        IncidentDraft draft = buildDraft(Impact.LOW, Urgency.LOW, 1);
        assertThrows(InvalidInputException.class, () -> factory.create(draft, null));
    }

    @Test
    void constructor_nullIds_throwsInvalidInput() {
        assertThrows(InvalidInputException.class, () -> new IncidentFactory(null, strategy));
    }

    @Test
    void constructor_nullStrategy_throwsInvalidInput() {
        assertThrows(InvalidInputException.class, () -> new IncidentFactory(idGen, null));
    }

    @Test
    void create_newIncidentHasNoDeadlines() {
        IncidentDraft draft = buildDraft(Impact.LOW, Urgency.LOW, 1);

        Incident inc = factory.create(draft, CREATED_AT);

        assertTrue(inc.getResponseDeadline().isEmpty());
        assertTrue(inc.getResolutionDeadline().isEmpty());
    }

    @Test
    void create_newIncidentHasNoAssignment() {
        IncidentDraft draft = buildDraft(Impact.LOW, Urgency.LOW, 1);

        Incident inc = factory.create(draft, CREATED_AT);

        assertTrue(inc.getAssignedTechnician().isEmpty());
        assertTrue(inc.getAssignedAt().isEmpty());
    }
}

