package com.iitsm.domain.sla;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.MissingSlaException;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.SlaPolicy;
import com.iitsm.domain.repository.SlaPolicyRepository;
import com.iitsm.infrastructure.persistence.memory.InMemorySlaPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SlaPolicyResolverTest {

    private SlaPolicyRepository repository;
    private SlaPolicyResolver resolver;

    @BeforeEach
    void setUp() {
        repository = new InMemorySlaPolicyRepository();
        resolver = new SlaPolicyResolver(repository);
    }

    @Test
    @DisplayName("resolve returns override policy when present for service and priority")
    void resolve_overridePresent_returnsOverridePolicy() {
        SlaPolicy defaultPolicy = new SlaPolicy(null, PriorityLevel.P1, Duration.ofMinutes(5), Duration.ofMinutes(30));
        SlaPolicy overridePolicy = new SlaPolicy("SRV-CRITICAL", PriorityLevel.P1, Duration.ofMinutes(2), Duration.ofMinutes(15));

        repository.save(defaultPolicy);
        repository.save(overridePolicy);

        SlaPolicy resolved = resolver.resolve("SRV-CRITICAL", PriorityLevel.P1);
        assertEquals(overridePolicy, resolved);
        assertEquals(Duration.ofMinutes(2), resolved.responseTarget());
        assertEquals(Duration.ofMinutes(15), resolved.resolutionTarget());
    }

    @Test
    @DisplayName("resolve falls back to default policy when no service override exists")
    void resolve_noOverride_fallsBackToDefaultPolicy() {
        SlaPolicy defaultPolicy = new SlaPolicy(null, PriorityLevel.P2, Duration.ofMinutes(15), Duration.ofHours(2));
        repository.save(defaultPolicy);

        SlaPolicy resolved = resolver.resolve("SRV-STANDARD", PriorityLevel.P2);
        assertEquals(defaultPolicy, resolved);
        assertTrue(resolved.isDefault());
    }

    @Test
    @DisplayName("resolve throws MissingSlaException when neither override nor default exists")
    void resolve_noPolicyConfigured_throwsMissingSlaException() {
        MissingSlaException ex = assertThrows(
                MissingSlaException.class,
                () -> resolver.resolve("SRV-UNKNOWN", PriorityLevel.P1)
        );
        assertEquals("MISSING_SLA", ex.getCode());
        assertTrue(ex.getMessage().contains("SRV-UNKNOWN"));
        assertTrue(ex.getMessage().contains("P1"));
    }

    @Test
    @DisplayName("null repository or priority throws InvalidInputException")
    void resolve_nullArguments_throwsInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> new SlaPolicyResolver(null));
        assertThrows(InvalidInputException.class, () -> resolver.resolve("SRV-01", null));
    }
}
