package com.iitsm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iitsm.api.dto.CheckDuplicateRequest;
import com.iitsm.api.dto.CheckDuplicateResponse;
import com.iitsm.application.command.CreateIncidentCommand;
import com.iitsm.application.service.DuplicateDetectionService;
import com.iitsm.application.service.ServiceFixture;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.Urgency;

class DuplicateDetectionControllerTest {

    private ServiceFixture fixture;
    private DuplicateDetectionService duplicateDetectionService;
    private DuplicateDetectionController controller;

    @BeforeEach
    void setUp() {
        fixture = new ServiceFixture();
        duplicateDetectionService = new DuplicateDetectionService(fixture.incidents);
        controller = new DuplicateDetectionController(duplicateDetectionService);
    }

    @Test
    @DisplayName("checkDuplicates returns empty when query has no matching incidents")
    void checkDuplicates_noMatch_returnsEmpty() {
        CheckDuplicateResponse response = controller.checkDuplicates(new CheckDuplicateRequest(
                "Unique title never seen before",
                "Some description",
                fixture.itService.getId(),
                fixture.category.getId()
        ));

        assertNotNull(response);
        assertFalse(response.hasDuplicates());
        assertTrue(response.candidates().isEmpty());
    }

    @Test
    @DisplayName("checkDuplicates finds matching open incidents and returns advisory candidates")
    void checkDuplicates_findsMatches() {
        Incident inc = fixture.service.createIncident(new CreateIncidentCommand(
                fixture.employee.getId(),
                "VPN connection failure when connecting remotely",
                "VPN client times out",
                fixture.itService.getId(),
                fixture.category.getId(),
                null,
                CategorySource.MANUAL,
                Impact.HIGH,
                Urgency.HIGH,
                10
        ));

        CheckDuplicateResponse response = controller.checkDuplicates(new CheckDuplicateRequest(
                "VPN connection failure",
                "Cannot reach office network",
                fixture.itService.getId(),
                fixture.category.getId()
        ));

        assertNotNull(response);
        assertTrue(response.hasDuplicates());
        assertEquals(1, response.candidates().size());
        assertEquals(inc.getId(), response.candidates().get(0).id());
        assertEquals("VPN connection failure when connecting remotely", response.candidates().get(0).title());
    }

    @Test
    @DisplayName("checkDuplicates handles null request payload safely")
    void checkDuplicates_nullRequest_handledSafely() {
        CheckDuplicateResponse response = controller.checkDuplicates(null);
        assertNotNull(response);
        assertFalse(response.hasDuplicates());
        assertTrue(response.candidates().isEmpty());
    }
}
