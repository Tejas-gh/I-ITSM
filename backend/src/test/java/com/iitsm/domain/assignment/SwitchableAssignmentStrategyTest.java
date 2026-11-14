package com.iitsm.domain.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.priority.PriorityResult;
import com.iitsm.support.TestData;

class SwitchableAssignmentStrategyTest {

    private final Incident networkIncident = new Incident("INC1001",
            TestData.draft(TestData.employee("EMP01"), TestData.service("SRV1", BusinessCriticality.HIGH),
                    TestData.category("NETWORK"), Impact.HIGH, Urgency.HIGH, 1),
            new PriorityResult(27, PriorityLevel.P2), Instant.parse("2026-11-16T09:00:00Z"));

    private static Technician tech(String id, int networkSkill) {
        return new Technician(id, "Tech " + id, id.toLowerCase() + "@corp.com", Role.TECHNICIAN,
                Map.of("NETWORK", networkSkill), 5, 5);
    }

    private SwitchableAssignmentStrategy newSwitchable() {
        return new SwitchableAssignmentStrategy(
                List.of(new SkillBasedAssignmentStrategy(), new LoadBasedAssignmentStrategy()), "skill-based");
    }

    @Test
    void initialSelection_isTheRequestedStrategy() {
        SwitchableAssignmentStrategy strategy = newSwitchable();

        assertEquals("skill-based", strategy.selectedName());
        assertEquals("skill-based", strategy.name());
        assertEquals(List.of("skill-based", "load-based"), strategy.availableNames());
    }

    @Test
    void select_changesWhichStrategyRanks() {
        Technician skilledButBusy = tech("T-SKILLED", 5);
        Technician unskilledButIdle = tech("T-IDLE", 1);
        List<Technician> candidates = List.of(skilledButBusy, unskilledButIdle);
        Map<String, Integer> load = Map.of("T-SKILLED", 3, "T-IDLE", 0);
        SwitchableAssignmentStrategy strategy = newSwitchable();

        assertEquals("T-SKILLED", strategy.pick(networkIncident, candidates, load).orElseThrow().getId());

        strategy.select("load-based");

        assertEquals("load-based", strategy.name());
        assertEquals("T-IDLE", strategy.pick(networkIncident, candidates, load).orElseThrow().getId());

        strategy.select("skill-based");

        assertEquals("T-SKILLED", strategy.pick(networkIncident, candidates, load).orElseThrow().getId());
    }

    @Test
    void select_unknownOrBlankName_isRejectedAndKeepsTheCurrentStrategy() {
        SwitchableAssignmentStrategy strategy = newSwitchable();

        assertThrows(InvalidInputException.class, () -> strategy.select("round-robin"));
        assertThrows(InvalidInputException.class, () -> strategy.select(" "));
        assertThrows(InvalidInputException.class, () -> strategy.select(null));

        assertEquals("skill-based", strategy.selectedName());
    }

    @Test
    void constructor_invalidArguments_throwInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> new SwitchableAssignmentStrategy(null, "skill-based"));
        assertThrows(InvalidInputException.class, () -> new SwitchableAssignmentStrategy(List.of(), "skill-based"));
        assertThrows(InvalidInputException.class, () -> new SwitchableAssignmentStrategy(
                List.of(new SkillBasedAssignmentStrategy()), "load-based"));
        assertThrows(InvalidInputException.class, () -> new SwitchableAssignmentStrategy(
                List.of(new SkillBasedAssignmentStrategy(), new SkillBasedAssignmentStrategy()), "skill-based"));
    }

    @Test
    void availableNames_isUnmodifiable() {
        SwitchableAssignmentStrategy strategy = newSwitchable();

        assertThrows(UnsupportedOperationException.class, () -> strategy.availableNames().clear());
    }
}
