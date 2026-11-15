package com.iitsm.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Role;

class TechnicianRecommendationTest {

    @Test
    void validRecommendationConstructsSuccessfully() {
        TechnicianRecommendation rec = new TechnicianRecommendation(
                "T1", "Alice", "alice@corp.com", Role.TECHNICIAN,
                92.0, "Skill level 5", 2, 5, Map.of("NETWORK", 5), 8
        );

        assertEquals("T1", rec.technicianId());
        assertEquals("Alice", rec.name());
        assertEquals("alice@corp.com", rec.email());
        assertEquals(Role.TECHNICIAN, rec.role());
        assertEquals(92.0, rec.score());
        assertEquals("Skill level 5", rec.reason());
        assertEquals(2, rec.activeWorkload());
        assertEquals(5, rec.maxCapacity());
        assertEquals(5, rec.skillLevels().get("NETWORK"));
        assertEquals(8, rec.yearsOfExperience());
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(InvalidInputException.class, () ->
                new TechnicianRecommendation("", "Alice", "a@corp.com", Role.TECHNICIAN, 1.0, "R", 0, 5, null, 1));
        assertThrows(InvalidInputException.class, () ->
                new TechnicianRecommendation("T1", "", "a@corp.com", Role.TECHNICIAN, 1.0, "R", 0, 5, null, 1));
        assertThrows(InvalidInputException.class, () ->
                new TechnicianRecommendation("T1", "Alice", "", Role.TECHNICIAN, 1.0, "R", 0, 5, null, 1));
        assertThrows(InvalidInputException.class, () ->
                new TechnicianRecommendation("T1", "Alice", "a@corp.com", null, 1.0, "R", 0, 5, null, 1));
        assertThrows(InvalidInputException.class, () ->
                new TechnicianRecommendation("T1", "Alice", "a@corp.com", Role.TECHNICIAN, Double.NaN, "R", 0, 5, null, 1));
        assertThrows(InvalidInputException.class, () ->
                new TechnicianRecommendation("T1", "Alice", "a@corp.com", Role.TECHNICIAN, 1.0, "", 0, 5, null, 1));
        assertThrows(InvalidInputException.class, () ->
                new TechnicianRecommendation("T1", "Alice", "a@corp.com", Role.TECHNICIAN, 1.0, "R", -1, 5, null, 1));
        assertThrows(InvalidInputException.class, () ->
                new TechnicianRecommendation("T1", "Alice", "a@corp.com", Role.TECHNICIAN, 1.0, "R", 0, 0, null, 1));
        assertThrows(InvalidInputException.class, () ->
                new TechnicianRecommendation("T1", "Alice", "a@corp.com", Role.TECHNICIAN, 1.0, "R", 0, 5, null, -1));
    }
}
