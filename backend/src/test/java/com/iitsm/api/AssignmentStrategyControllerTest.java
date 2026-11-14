package com.iitsm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.iitsm.api.dto.StrategyResponse;
import com.iitsm.api.dto.StrategySelectionRequest;
import com.iitsm.application.service.AdminPermissionPolicy;
import com.iitsm.application.service.AssignmentStrategyAdminService;
import com.iitsm.domain.assignment.LoadBasedAssignmentStrategy;
import com.iitsm.domain.assignment.SkillBasedAssignmentStrategy;
import com.iitsm.domain.assignment.SwitchableAssignmentStrategy;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.TestData;

class AssignmentStrategyControllerTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final SwitchableAssignmentStrategy switchable = new SwitchableAssignmentStrategy(
            List.of(new SkillBasedAssignmentStrategy(), new LoadBasedAssignmentStrategy()), "skill-based");
    private final AssignmentStrategyController controller = new AssignmentStrategyController(
            new AssignmentStrategyAdminService(users, new AdminPermissionPolicy(), switchable));

    AssignmentStrategyControllerTest() {
        users.save(TestData.admin("ADMIN01"));
        users.save(TestData.employee("EMP01"));
        users.save(TestData.technician("TECH01", 3));
    }

    private static Authentication as(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, "n/a");
    }

    @Test
    void get_returnsTheActiveStrategyAndTheAvailableOnes() {
        StrategyResponse response = controller.get(as("ADMIN01"));

        assertEquals("skill-based", response.selected());
        assertEquals(List.of("skill-based", "load-based"), response.available());
    }

    @Test
    void select_switchesTheStrategyThatTheSystemUses() {
        StrategyResponse response = controller.select(new StrategySelectionRequest("load-based"), as("ADMIN01"));

        assertEquals("load-based", response.selected());
        assertEquals("load-based", controller.get(as("ADMIN01")).selected());
        assertEquals("load-based", switchable.selectedName());

        controller.select(new StrategySelectionRequest("skill-based"), as("ADMIN01"));

        assertEquals("skill-based", switchable.selectedName());
    }

    @Test
    void select_unknownOrMissingStrategy_isRejectedAndNothingChanges() {
        assertThrows(InvalidInputException.class,
                () -> controller.select(new StrategySelectionRequest("round-robin"), as("ADMIN01")));
        assertThrows(InvalidInputException.class,
                () -> controller.select(new StrategySelectionRequest(null), as("ADMIN01")));

        assertEquals("skill-based", switchable.selectedName());
    }

    @Test
    void everyEndpoint_nonAdministrator_isRejectedWithAnUnauthorizedOperationThatMapsTo403() {
        for (String actor : List.of("EMP01", "TECH01")) {
            UnauthorizedOperationException ex = assertThrows(UnauthorizedOperationException.class,
                    () -> controller.get(as(actor)));
            assertEquals(403, ApiErrorStatus.httpStatusFor(ex.getCode()));
            assertThrows(UnauthorizedOperationException.class,
                    () -> controller.select(new StrategySelectionRequest("load-based"), as(actor)));
        }
        assertEquals("skill-based", switchable.selectedName());
    }
}
