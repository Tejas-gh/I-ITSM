package com.iitsm.domain.escalation;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentDraft;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.priority.PriorityResult;
import com.iitsm.support.TestData;

import static org.junit.jupiter.api.Assertions.*;

class EscalationChainTest {

    private static final Instant T0 = Instant.parse("2026-11-10T10:00:00Z");
    private static final Duration TARGET = Duration.ofMinutes(15);
    private static final Instant T1 = T0.plus(TARGET); // 10:15 - response deadline
    private static final Instant T2 = T1.plus(TARGET); // 10:30 - level 2
    private static final Instant T3 = T2.plus(TARGET); // 10:45 - level 3

    private Incident incident;
    private EscalationHandler chain;

    @BeforeEach
    void setUp() {
        chain = com.iitsm.application.service.EscalationService.buildDefaultChain();

        IncidentDraft draft = TestData.draft(
                TestData.employee("EMP01"),
                TestData.service("SVC01", BusinessCriticality.CRITICAL),
                TestData.category("CAT01"),
                Impact.CRITICAL,
                Urgency.HIGH,
                1
        );
        PriorityResult priority = new PriorityResult(95, PriorityLevel.P1);
        incident = new Incident("INC-P1-001", draft, priority, T0);
        // Set response deadline
        incident.applySlaDeadlines(T1, T0.plus(Duration.ofHours(2)));
    }

    @Test
    @DisplayName("Before response deadline: no escalation triggered")
    void beforeDeadline_noEscalation() {
        EscalationContext ctx = new EscalationContext(incident, TARGET, T0.plus(Duration.ofMinutes(10)), Set.of());
        Optional<EscalationDecision> decision = chain.handle(ctx);
        assertTrue(decision.isEmpty());
    }

    @Test
    @DisplayName("At response deadline: triggers Level 1 to Team Lead")
    void atDeadline_triggersLevel1() {
        EscalationContext ctx = new EscalationContext(incident, TARGET, T1, Set.of());
        Optional<EscalationDecision> decision = chain.handle(ctx);

        assertTrue(decision.isPresent());
        assertEquals(1, decision.get().level());
        assertEquals(Role.TEAM_LEAD, decision.get().targetRole());
        assertTrue(decision.get().reason().contains("Team Lead"));
    }

    @Test
    @DisplayName("When Level 1 is already recorded and time < Level 2: returns empty")
    void level1Recorded_beforeLevel2_returnsEmpty() {
        EscalationContext ctx = new EscalationContext(incident, TARGET, T1.plus(Duration.ofMinutes(5)), Set.of(1));
        Optional<EscalationDecision> decision = chain.handle(ctx);
        assertTrue(decision.isEmpty());
    }

    @Test
    @DisplayName("When Level 1 is already recorded and time reaches Level 2: triggers Level 2 to Administrator")
    void level1Recorded_atLevel2_triggersLevel2() {
        EscalationContext ctx = new EscalationContext(incident, TARGET, T2, Set.of(1));
        Optional<EscalationDecision> decision = chain.handle(ctx);

        assertTrue(decision.isPresent());
        assertEquals(2, decision.get().level());
        assertEquals(Role.ADMINISTRATOR, decision.get().targetRole());
        assertTrue(decision.get().reason().contains("Administrator"));
    }

    @Test
    @DisplayName("When Level 1 & 2 recorded and time reaches Level 3: triggers Level 3 to IT Manager")
    void level2Recorded_atLevel3_triggersLevel3() {
        EscalationContext ctx = new EscalationContext(incident, TARGET, T3, Set.of(1, 2));
        Optional<EscalationDecision> decision = chain.handle(ctx);

        assertTrue(decision.isPresent());
        assertEquals(3, decision.get().level());
        assertEquals(Role.IT_MANAGER, decision.get().targetRole());
        assertTrue(decision.get().reason().contains("IT Manager"));
    }

    @Test
    @DisplayName("When all 3 levels already executed: returns empty")
    void allLevelsExecuted_returnsEmpty() {
        EscalationContext ctx = new EscalationContext(incident, TARGET, T3.plus(Duration.ofHours(1)), Set.of(1, 2, 3));
        Optional<EscalationDecision> decision = chain.handle(ctx);
        assertTrue(decision.isEmpty());
    }
}
