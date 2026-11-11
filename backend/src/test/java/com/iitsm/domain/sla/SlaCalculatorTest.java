package com.iitsm.domain.sla;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.MissingSlaException;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.SlaPolicy;
import com.iitsm.domain.repository.SlaPolicyRepository;
import com.iitsm.infrastructure.persistence.memory.InMemorySlaPolicyRepository;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SlaCalculatorTest {

    private SlaPolicyRepository repository;
    private SlaCalculator calculator;
    private final Instant baseTime = Instant.parse("2026-11-16T09:00:00Z");

    @BeforeEach
    void setUp() {
        repository = new InMemorySlaPolicyRepository();
        for (SlaPolicy policy : TestData.defaultSlaPolicies()) {
            repository.save(policy);
        }
        SlaPolicyResolver resolver = new SlaPolicyResolver(repository);
        calculator = new SlaCalculator(resolver);
    }

    @ParameterizedTest
    @CsvSource({
            "P1, 300, 1800",       // 5 min, 30 min
            "P2, 900, 7200",       // 15 min, 2 h
            "P3, 3600, 28800",     // 1 h, 8 h
            "P4, 14400, 86400"     // 4 h, 24 h
    })
    @DisplayName("calculate calculates correct deadlines for four default spec rows")
    void calculate_defaultSpecRows_producesExpectedDeadlines(
            PriorityLevel priority,
            long expectedResponseSeconds,
            long expectedResolutionSeconds
    ) {
        SlaDeadlines deadlines = calculator.calculate("SRV-STD", priority, baseTime);

        assertEquals(baseTime.plusSeconds(expectedResponseSeconds), deadlines.responseDeadline());
        assertEquals(baseTime.plusSeconds(expectedResolutionSeconds), deadlines.resolutionDeadline());
    }

    @Test
    @DisplayName("calculate honors per-service override targets")
    void calculate_serviceOverride_producesOverriddenDeadlines() {
        repository.save(new SlaPolicy("SRV-FAST", PriorityLevel.P1, Duration.ofMinutes(1), Duration.ofMinutes(10)));

        SlaDeadlines deadlines = calculator.calculate("SRV-FAST", PriorityLevel.P1, baseTime);

        assertEquals(baseTime.plus(Duration.ofMinutes(1)), deadlines.responseDeadline());
        assertEquals(baseTime.plus(Duration.ofMinutes(10)), deadlines.resolutionDeadline());
    }

    @Test
    @DisplayName("calculate throws MissingSlaException when policy is missing")
    void calculate_missingPolicy_throwsMissingSlaException() {
        SlaPolicyRepository emptyRepo = new InMemorySlaPolicyRepository();
        SlaCalculator emptyCalculator = new SlaCalculator(new SlaPolicyResolver(emptyRepo));

        assertThrows(MissingSlaException.class, () -> emptyCalculator.calculate("SRV-X", PriorityLevel.P1, baseTime));
    }

    @Test
    @DisplayName("calculate validates null arguments")
    void calculate_nullArguments_throwsInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> new SlaCalculator(null));
        assertThrows(InvalidInputException.class, () -> calculator.calculate("SRV-01", null, baseTime));
        assertThrows(InvalidInputException.class, () -> calculator.calculate("SRV-01", PriorityLevel.P1, null));
    }
}
