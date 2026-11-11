package com.iitsm.domain.assignment;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.priority.PriorityResult;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillBasedAssignmentStrategyTest {

    private final SkillBasedAssignmentStrategy strategy = new SkillBasedAssignmentStrategy();

    private final Incident networkIncident = incidentIn("NETWORK");

    private static Incident incidentIn(String categoryId) {
        var draft = TestData.draft(TestData.employee("EMP01"),
                TestData.service("SRV1", BusinessCriticality.HIGH),
                TestData.category(categoryId), Impact.HIGH, Urgency.HIGH, 1);
        return new Incident("INC1001", draft, new PriorityResult(27, PriorityLevel.P2),
                Instant.parse("2026-11-16T09:00:00Z"));
    }

    private static Technician tech(String id, Map<String, Integer> skills, int years, int maxActive) {
        return new Technician(id, "Tech " + id, id.toLowerCase() + "@corp.com",
                Role.TECHNICIAN, skills, years, maxActive);
    }

    private static Technician networkTech(String id, int level, int years) {
        return tech(id, Map.of("NETWORK", level), years, 5);
    }

    private static Map<String, Integer> workload(Object... idAndCount) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < idAndCount.length; i += 2) {
            map.put((String) idAndCount[i], (Integer) idAndCount[i + 1]);
        }
        return map;
    }

    private static List<String> ids(List<TechnicianScore> scores) {
        return scores.stream().map(s -> s.technician().getId()).toList();
    }

    @Test
    void name_returnsSkillBased() {
        assertEquals("skill-based", strategy.name());
    }

    @Test
    void rank_singleEligibleTechnician_returnsItWithSkillLevelAsScore() {
        Technician t = networkTech("T1", 4, 3);

        List<TechnicianScore> result = strategy.rank(networkIncident, List.of(t), workload("T1", 1));

        assertEquals(1, result.size());
        assertEquals(t, result.get(0).technician());
        assertEquals(4.0, result.get(0).score());
        assertFalse(result.get(0).reason().isBlank());
    }

    @Test
    void rank_multipleEligible_higherSkillRanksFirstWithDescendingScores() {
        Technician low = networkTech("T1", 2, 10);
        Technician high = networkTech("T2", 5, 1);
        Technician mid = networkTech("T3", 3, 5);

        List<TechnicianScore> result = strategy.rank(networkIncident, List.of(low, high, mid),
                workload("T1", 0, "T2", 0, "T3", 0));

        assertEquals(List.of("T2", "T3", "T1"), ids(result));
        assertEquals(List.of(5.0, 3.0, 2.0), result.stream().map(TechnicianScore::score).toList());
    }

    @Test
    void rank_equalSkill_lessLoadedTechnicianFirst() {
        Technician busy = networkTech("T1", 3, 5);
        Technician idle = networkTech("T2", 3, 5);

        List<TechnicianScore> result = strategy.rank(networkIncident, List.of(busy, idle),
                workload("T1", 3, "T2", 1));

        assertEquals(List.of("T2", "T1"), ids(result));
    }

    @Test
    void rank_equalSkillAndLoad_moreExperiencedFirst() {
        Technician junior = networkTech("T1", 3, 2);
        Technician senior = networkTech("T2", 3, 9);

        List<TechnicianScore> result = strategy.rank(networkIncident, List.of(junior, senior),
                workload("T1", 1, "T2", 1));

        assertEquals(List.of("T2", "T1"), ids(result));
    }

    @Test
    void rank_completeTie_orderedByTechnicianIdDeterministically() {
        Technician b = networkTech("T-B", 3, 5);
        Technician a = networkTech("T-A", 3, 5);

        List<TechnicianScore> result = strategy.rank(networkIncident, List.of(b, a),
                workload("T-B", 1, "T-A", 1));

        assertEquals(List.of("T-A", "T-B"), ids(result));
    }

    @Test
    void rank_usesSkillOfTheIncidentsCategory() {
        Technician networkExpert = tech("T1", Map.of("NETWORK", 5, "DATABASE", 1), 5, 5);
        Technician databaseExpert = tech("T2", Map.of("NETWORK", 1, "DATABASE", 5), 5, 5);
        Map<String, Integer> load = workload("T1", 0, "T2", 0);

        assertEquals("T1", ids(strategy.rank(incidentIn("NETWORK"),
                List.of(networkExpert, databaseExpert), load)).get(0));
        assertEquals("T2", ids(strategy.rank(incidentIn("DATABASE"),
                List.of(networkExpert, databaseExpert), load)).get(0));
    }

    @Test
    void rank_technicianWithoutCategorySkill_isOmitted() {
        Technician unskilled = tech("T1", Map.of("DATABASE", 5), 20, 5);
        Technician skilled = networkTech("T2", 1, 0);

        List<TechnicianScore> result = strategy.rank(networkIncident, List.of(unskilled, skilled),
                workload("T1", 0, "T2", 0));

        assertEquals(List.of("T2"), ids(result));
    }

    @Test
    void rank_technicianWithEmptySkillMap_isOmitted() {
        Technician none = tech("T1", Map.of(), 5, 5);

        assertTrue(strategy.rank(networkIncident, List.of(none), workload("T1", 0)).isEmpty());
    }

    @Test
    void rank_unavailableTechnician_isOmitted() {
        Technician unavailable = networkTech("T1", 5, 10);
        unavailable.markUnavailable();
        Technician available = networkTech("T2", 2, 1);

        List<TechnicianScore> result = strategy.rank(networkIncident, List.of(unavailable, available),
                workload("T1", 0, "T2", 0));

        assertEquals(List.of("T2"), ids(result));
    }

    @Test
    void rank_inactiveTechnician_isOmitted() {
        Technician inactive = networkTech("T1", 5, 10);
        inactive.deactivate();
        Technician active = networkTech("T2", 2, 1);

        List<TechnicianScore> result = strategy.rank(networkIncident, List.of(inactive, active),
                workload("T1", 0, "T2", 0));

        assertEquals(List.of("T2"), ids(result));
    }

    @Test
    void rank_overloadedTechnician_isOmittedAtTheLimitButKeptJustBelow() {
        Technician atLimit = tech("T1", Map.of("NETWORK", 5), 5, 3);
        Technician belowLimit = tech("T2", Map.of("NETWORK", 2), 5, 3);

        List<TechnicianScore> result = strategy.rank(networkIncident, List.of(atLimit, belowLimit),
                workload("T1", 3, "T2", 2));

        assertEquals(List.of("T2"), ids(result));
    }

    @Test
    void rank_noCandidates_returnsEmptyList() {
        assertTrue(strategy.rank(networkIncident, List.of(), workload()).isEmpty());
    }

    @Test
    void rank_noEligibleTechnician_returnsEmptyAndPickReturnsEmpty() {
        Technician unskilled = tech("T1", Map.of("DATABASE", 5), 5, 5);
        Technician overloaded = tech("T2", Map.of("NETWORK", 5), 5, 1);
        List<Technician> candidates = List.of(unskilled, overloaded);
        Map<String, Integer> load = workload("T1", 0, "T2", 1);

        assertTrue(strategy.rank(networkIncident, candidates, load).isEmpty());
        assertEquals(Optional.empty(), strategy.pick(networkIncident, candidates, load));
    }

    @Test
    void pick_returnsBestRankedTechnician() {
        Technician second = networkTech("T1", 3, 5);
        Technician best = networkTech("T2", 4, 5);

        Optional<Technician> picked = strategy.pick(networkIncident, List.of(second, best),
                workload("T1", 0, "T2", 0));

        assertEquals(Optional.of(best), picked);
    }

    @Test
    void rank_teamLead_isTreatedLikeAnyOtherTechnician() {
        Technician lead = new Technician("L1", "Lead", "l1@corp.com", Role.TEAM_LEAD,
                Map.of("NETWORK", 5), 8, 5);

        assertEquals(List.of("L1"), ids(strategy.rank(networkIncident, List.of(lead), workload("L1", 0))));
    }

    @Test
    void rank_result_isUnmodifiable() {
        List<TechnicianScore> result = strategy.rank(networkIncident,
                List.of(networkTech("T1", 3, 5)), workload("T1", 0));

        assertThrows(UnsupportedOperationException.class, result::clear);
    }

    @Test
    void rank_doesNotMutateItsArguments() {
        Technician t1 = networkTech("T1", 2, 5);
        Technician t2 = networkTech("T2", 5, 5);
        List<Technician> candidates = new ArrayList<>(List.of(t1, t2));
        Map<String, Integer> load = workload("T1", 1, "T2", 2);

        strategy.rank(networkIncident, candidates, load);

        assertEquals(List.of(t1, t2), candidates);
        assertEquals(workload("T1", 1, "T2", 2), load);
        assertTrue(t1.isAssignable());
        assertTrue(t2.isAssignable());
    }

    @Test
    void rank_nullArguments_throwInvalidInputException() {
        List<Technician> candidates = List.of(networkTech("T1", 3, 5));
        Map<String, Integer> load = workload("T1", 0);

        assertThrows(InvalidInputException.class, () -> strategy.rank(null, candidates, load));
        assertThrows(InvalidInputException.class, () -> strategy.rank(networkIncident, null, load));
        assertThrows(InvalidInputException.class, () -> strategy.rank(networkIncident, candidates, null));
    }

    @Test
    void rank_nullCandidateElement_throwsInvalidInputException() {
        List<Technician> candidates = new ArrayList<>();
        candidates.add(null);

        assertThrows(InvalidInputException.class,
                () -> strategy.rank(networkIncident, candidates, workload()));
    }

    @Test
    void rank_missingWorkloadEntry_throwsInvalidInputException() {
        List<Technician> candidates = List.of(networkTech("T1", 3, 5));

        InvalidInputException ex = assertThrows(InvalidInputException.class,
                () -> strategy.rank(networkIncident, candidates, workload()));
        assertNotNull(ex.getMessage());
    }

    @Test
    void rank_negativeWorkload_throwsInvalidInputException() {
        List<Technician> candidates = List.of(networkTech("T1", 3, 5));

        assertThrows(InvalidInputException.class,
                () -> strategy.rank(networkIncident, candidates, workload("T1", -1)));
    }
}
