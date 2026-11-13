package com.iitsm.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.iitsm.domain.factory.IncidentIdGenerator;

/** The generator is backed by the database sequence from V5, so ids are well-formed, increasing and unique. */
@SpringBootTest
@ActiveProfiles("test")
class JpaIncidentIdGeneratorTest {

    @Autowired
    private IncidentIdGenerator ids;

    private static long number(String id) {
        assertTrue(id.matches("INC\\d+"), id);
        return Long.parseLong(id.substring(3));
    }

    @Test
    void nextIncidentId_returnsIncidentPrefixedIncreasingNumbersFrom1001() {
        long first = number(ids.nextIncidentId());
        long second = number(ids.nextIncidentId());

        assertTrue(first >= 1001);
        assertEquals(first + 1, second);
    }

    @Test
    void nextIncidentId_neverRepeats() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            assertTrue(seen.add(ids.nextIncidentId()));
        }
        assertNotEquals(ids.nextIncidentId(), ids.nextIncidentId());
    }
}
