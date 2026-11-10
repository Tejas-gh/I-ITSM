package com.iitsm.domain.priority;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Urgency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeightedPriorityStrategyTest {

    private final PriorityStrategy strategy = new WeightedPriorityStrategy();

    private PriorityResult calc(Impact i, Urgency u, BusinessCriticality c, int users) {
        return strategy.calculate(new PriorityInput(i, u, c, users));
    }

    // §K required oracle table
    @ParameterizedTest(name = "{0}/{1}/{2}/{3} users -> {4} {5}")
    @CsvSource({
            "CRITICAL, CRITICAL, CRITICAL, 1,   64, P1",
            "CRITICAL, CRITICAL, CRITICAL, 200, 76, P1",
            "HIGH,     HIGH,     CRITICAL, 1,   36, P1",
            "CRITICAL, CRITICAL, MEDIUM,   1,   32, P2",
            "CRITICAL, CRITICAL, MEDIUM,   10,  36, P1",
            "HIGH,     HIGH,     HIGH,     50,  35, P2",
            "MEDIUM,   HIGH,     HIGH,     1,   18, P2",
            "HIGH,     HIGH,     LOW,      50,  17, P3",
            "MEDIUM,   MEDIUM,   MEDIUM,   1,   8,  P3",
            "LOW,      LOW,      HIGH,     10,  7,  P4",
            "LOW,      LOW,      LOW,      1,   1,  P4"
    })
    void calculate_oracleTable_returnsExpectedScoreAndLevel(Impact impact, Urgency urgency,
                                                           BusinessCriticality criticality,
                                                           int users, int score, PriorityLevel level) {
        PriorityResult result = calc(impact, urgency, criticality, users);
        assertEquals(score, result.score());
        assertEquals(level, result.level());
    }

    // base = 1 (LOW x LOW x LOW), so score = 1 + bump
    @ParameterizedTest(name = "{0} users -> bump {1}")
    @CsvSource({"1,0", "9,0", "10,4", "49,4", "50,8", "199,8", "200,12", "5000,12"})
    void calculate_affectedUsersBands_addExpectedBump(int users, int bump) {
        PriorityResult result = calc(Impact.LOW, Urgency.LOW, BusinessCriticality.LOW, users);
        assertEquals(1 + bump, result.score());
    }

    @Test
    void calculate_thresholdBoundaries_areInclusive() {
        // 35 -> P2, 36 -> P1
        assertEquals(PriorityLevel.P2, calc(Impact.HIGH, Urgency.HIGH, BusinessCriticality.HIGH, 50).level());
        assertEquals(PriorityLevel.P1, calc(Impact.HIGH, Urgency.HIGH, BusinessCriticality.CRITICAL, 1).level());
        // 17 -> P3, 18 -> P2
        assertEquals(PriorityLevel.P3, calc(Impact.HIGH, Urgency.HIGH, BusinessCriticality.LOW, 50).level());
        assertEquals(PriorityLevel.P2, calc(Impact.MEDIUM, Urgency.HIGH, BusinessCriticality.HIGH, 1).level());  // 2x3x3 = 18
        // 7 -> P4, 8 -> P3
        assertEquals(PriorityLevel.P4, calc(Impact.LOW, Urgency.LOW, BusinessCriticality.HIGH, 10).level());
        assertEquals(PriorityLevel.P3, calc(Impact.MEDIUM, Urgency.MEDIUM, BusinessCriticality.MEDIUM, 1).level());
    }

    @Test
    void calculate_maximumInput_scoreIs76() {
        PriorityResult result = calc(Impact.CRITICAL, Urgency.CRITICAL, BusinessCriticality.CRITICAL, Integer.MAX_VALUE);
        assertEquals(76, result.score());
        assertEquals(PriorityLevel.P1, result.level());
    }

    @Test
    void calculate_lowFactorDampensCriticalService() {
        // trivial impact and urgency on a critical service cannot reach P1
        PriorityResult result = calc(Impact.LOW, Urgency.LOW, BusinessCriticality.CRITICAL, 1);
        assertEquals(4, result.score());
        assertEquals(PriorityLevel.P4, result.level());
    }

    @Test
    void calculate_sameInputTwice_returnsEqualResults() {
        PriorityInput input = new PriorityInput(Impact.HIGH, Urgency.MEDIUM, BusinessCriticality.HIGH, 20);
        assertEquals(strategy.calculate(input), strategy.calculate(input));
        assertEquals(new PriorityResult(22, PriorityLevel.P2), strategy.calculate(input));
    }

    @Test
    void priorityInput_affectedUsersBelowOne_throwsInvalidInput() {
        assertThrows(InvalidInputException.class,
                () -> new PriorityInput(Impact.LOW, Urgency.LOW, BusinessCriticality.LOW, 0));
        assertThrows(InvalidInputException.class,
                () -> new PriorityInput(Impact.LOW, Urgency.LOW, BusinessCriticality.LOW, -5));
    }

    @Test
    void priorityInput_nullEnum_throwsInvalidInput() {
        assertThrows(InvalidInputException.class,
                () -> new PriorityInput(null, Urgency.LOW, BusinessCriticality.LOW, 1));
        assertThrows(InvalidInputException.class,
                () -> new PriorityInput(Impact.LOW, null, BusinessCriticality.LOW, 1));
        assertThrows(InvalidInputException.class,
                () -> new PriorityInput(Impact.LOW, Urgency.LOW, null, 1));
    }

    @Test
    void calculate_nullInput_throwsInvalidInput() {
        assertThrows(InvalidInputException.class, () -> strategy.calculate(null));
    }

    @Test
    void priorityResult_invalidScoreOrNullLevel_throwsInvalidInput() {
        assertThrows(InvalidInputException.class, () -> new PriorityResult(0, PriorityLevel.P4));
        assertThrows(InvalidInputException.class, () -> new PriorityResult(-1, PriorityLevel.P4));
        assertThrows(InvalidInputException.class, () -> new PriorityResult(1, null));
        assertEquals(1, new PriorityResult(1, PriorityLevel.P4).score());
    }
}
