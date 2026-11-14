package com.iitsm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.iitsm.api.dto.AvailabilityRequest;
import com.iitsm.api.dto.TechnicianResponse;
import com.iitsm.application.security.AccessToken;
import com.iitsm.application.service.AdminPermissionPolicy;
import com.iitsm.application.service.AuthService;
import com.iitsm.application.service.UserAdminService;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.exception.UserNotFoundException;
import com.iitsm.domain.factory.UserFactory;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.infrastructure.persistence.memory.InMemoryCategoryRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.FakeCredentialsRepository;
import com.iitsm.support.FakePasswordHasher;
import com.iitsm.support.TestData;

class TechnicianAdminControllerTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final TechnicianAdminController controller;

    TechnicianAdminControllerTest() {
        UserFactory factory = new UserFactory();
        AuthService authService = new AuthService(users, new FakeCredentialsRepository(), new FakePasswordHasher(),
                user -> new AccessToken("t", Instant.parse("2030-01-01T00:00:00Z")), factory);
        controller = new TechnicianAdminController(new UserAdminService(users, new InMemoryCategoryRepository(),
                new AdminPermissionPolicy(), factory, authService));
        users.save(TestData.admin("ADMIN01"));
        users.save(TestData.employee("EMP01"));
        users.save(new Technician("T1", "Tech One", "t1@corp.com", Role.TECHNICIAN, Map.of("NETWORK", 3), 5, 4));
        users.save(TestData.teamLead("LEAD1"));
    }

    private static Authentication as(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, "n/a");
    }

    @Test
    void list_returnsOnlyTechniciansIncludingTeamLeads() {
        List<TechnicianResponse> technicians = controller.list(as("ADMIN01"));

        assertEquals(List.of("T1", "LEAD1"), technicians.stream().map(TechnicianResponse::id).toList());
        TechnicianResponse first = technicians.get(0);
        assertEquals(Map.of("NETWORK", 3), first.skillLevels());
        assertEquals(5, first.yearsOfExperience());
        assertEquals(4, first.maxActiveIncidents());
        assertTrue(first.available());
        assertTrue(first.assignable());
    }

    @Test
    void technicianResponse_hasNoPasswordOrHashComponent() {
        List<String> names = Arrays.stream(TechnicianResponse.class.getRecordComponents())
                .map(RecordComponent::getName).map(String::toLowerCase).toList();

        assertTrue(names.stream().noneMatch(name -> name.contains("password") || name.contains("hash")));
    }

    @Test
    void setAvailability_updatesTheTechnicianAndIsVisibleInTheList() {
        TechnicianResponse off = controller.setAvailability("T1", new AvailabilityRequest(false), as("ADMIN01"));

        assertFalse(off.available());
        assertFalse(off.assignable());
        assertTrue(off.active());
        assertFalse(((Technician) users.findById("T1").orElseThrow()).isAvailable());
        assertFalse(controller.list(as("ADMIN01")).get(0).available());

        TechnicianResponse on = controller.setAvailability("T1", new AvailabilityRequest(true), as("ADMIN01"));

        assertTrue(on.available());
        assertTrue(on.assignable());
    }

    @Test
    void setAvailability_missingFlagNonTechnicianOrUnknownUser_isRejected() {
        assertThrows(InvalidInputException.class,
                () -> controller.setAvailability("T1", new AvailabilityRequest(null), as("ADMIN01")));
        assertThrows(InvalidInputException.class,
                () -> controller.setAvailability("EMP01", new AvailabilityRequest(false), as("ADMIN01")));
        assertThrows(UserNotFoundException.class,
                () -> controller.setAvailability("NO-SUCH", new AvailabilityRequest(false), as("ADMIN01")));
        assertTrue(((Technician) users.findById("T1").orElseThrow()).isAvailable());
    }

    @Test
    void everyEndpoint_nonAdministrator_isRejectedWithAnUnauthorizedOperationThatMapsTo403() {
        for (String actor : List.of("EMP01", "T1", "LEAD1")) {
            UnauthorizedOperationException ex = assertThrows(UnauthorizedOperationException.class,
                    () -> controller.list(as(actor)));
            assertEquals(403, ApiErrorStatus.httpStatusFor(ex.getCode()));
            assertThrows(UnauthorizedOperationException.class,
                    () -> controller.setAvailability("T1", new AvailabilityRequest(false), as(actor)));
        }
        assertTrue(((Technician) users.findById("T1").orElseThrow()).isAvailable());
    }
}
