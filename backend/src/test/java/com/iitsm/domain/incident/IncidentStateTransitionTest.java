package com.iitsm.domain.incident;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.InvalidStateTransitionException;
import com.iitsm.domain.model.IncidentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class IncidentStateTransitionTest {

    private record ActionInvoker(String name, Function<IncidentState, IncidentState> invoke) {}

    private static final Map<String, ActionInvoker> ACTIONS = Map.of(
            "classify", new ActionInvoker("classify", IncidentState::classify),
            "assign", new ActionInvoker("assign", IncidentState::assign),
            "unassign", new ActionInvoker("unassign", IncidentState::unassign),
            "acknowledge", new ActionInvoker("acknowledge", IncidentState::acknowledge),
            "startWork", new ActionInvoker("startWork", IncidentState::startWork),
            "requestInfo", new ActionInvoker("requestInfo", IncidentState::requestInfo),
            "receiveInfo", new ActionInvoker("receiveInfo", IncidentState::receiveInfo),
            "resolve", new ActionInvoker("resolve", IncidentState::resolve),
            "rejectResolution", new ActionInvoker("rejectResolution", IncidentState::rejectResolution),
            "verify", new ActionInvoker("verify", IncidentState::verify),
            "close", new ActionInvoker("close", IncidentState::close)
    );

    // The 12 valid (CurrentStatus, Action) -> ExpectedNextStatus transitions per §J.1
    private static final Map<String, IncidentStatus> VALID_TRANSITIONS = Map.ofEntries(
            Map.entry("NEW|classify", IncidentStatus.CLASSIFIED),
            Map.entry("CLASSIFIED|assign", IncidentStatus.ASSIGNED),
            Map.entry("ASSIGNED|acknowledge", IncidentStatus.ACKNOWLEDGED),
            Map.entry("ASSIGNED|unassign", IncidentStatus.CLASSIFIED),
            Map.entry("ACKNOWLEDGED|startWork", IncidentStatus.IN_PROGRESS),
            Map.entry("ACKNOWLEDGED|unassign", IncidentStatus.CLASSIFIED),
            Map.entry("IN_PROGRESS|requestInfo", IncidentStatus.WAITING_FOR_USER),
            Map.entry("IN_PROGRESS|resolve", IncidentStatus.RESOLVED),
            Map.entry("WAITING_FOR_USER|receiveInfo", IncidentStatus.IN_PROGRESS),
            Map.entry("RESOLVED|verify", IncidentStatus.VERIFIED),
            Map.entry("RESOLVED|rejectResolution", IncidentStatus.IN_PROGRESS),
            Map.entry("VERIFIED|close", IncidentStatus.CLOSED)
    );

    @Test
    @DisplayName("Assert all 99 state-action pairs: 12 valid transitions and 87 invalid transitions")
    void transition_allNinetyNineStateActionPairs_adhereToLifecycleMatrix() {
        int validCount = 0;
        int invalidCount = 0;

        for (IncidentStatus currentStatus : IncidentStatus.values()) {
            IncidentState state = IncidentStates.of(currentStatus);

            for (Map.Entry<String, ActionInvoker> entry : ACTIONS.entrySet()) {
                String actionName = entry.getKey();
                ActionInvoker invoker = entry.getValue();
                String transitionKey = currentStatus.name() + "|" + actionName;

                if (VALID_TRANSITIONS.containsKey(transitionKey)) {
                    IncidentStatus expectedNext = VALID_TRANSITIONS.get(transitionKey);
                    IncidentState nextState = invoker.invoke().apply(state);
                    assertNotNull(nextState, "Next state must not be null for " + transitionKey);
                    assertEquals(expectedNext, nextState.status(), "Unexpected next state for " + transitionKey);
                    validCount++;
                } else {
                    InvalidStateTransitionException ex = assertThrows(
                            InvalidStateTransitionException.class,
                            () -> invoker.invoke().apply(state),
                            "Expected InvalidStateTransitionException for invalid transition: " + transitionKey
                    );
                    assertEquals(currentStatus, ex.getCurrentStatus(), "Status mismatch in exception for " + transitionKey);
                    assertEquals(actionName, ex.getAction(), "Action mismatch in exception for " + transitionKey);
                    assertEquals("INVALID_STATE_TRANSITION", ex.getCode());
                    assertEquals("Cannot " + actionName + " an incident that is " + currentStatus, ex.getMessage());
                    invalidCount++;
                }
            }
        }

        assertEquals(12, validCount, "Must have exactly 12 valid transitions");
        assertEquals(87, invalidCount, "Must have exactly 87 invalid transitions");
        assertEquals(99, validCount + invalidCount, "Total pairs must equal 99");
    }

    @ParameterizedTest
    @EnumSource(IncidentStatus.class)
    @DisplayName("Assert guard predicates for each state match §J.2 table")
    void guardPredicates_allStates_matchContractMatrix(IncidentStatus status) {
        IncidentState state = IncidentStates.of(status);

        switch (status) {
            case NEW -> {
                assertFalse(state.allowsInvestigationNotes());
                assertTrue(state.allowsAdditionalInfo());
                assertFalse(state.allowsFeedback());
                assertTrue(state.allowsPriorityChange());
            }
            case CLASSIFIED -> {
                assertFalse(state.allowsInvestigationNotes());
                assertTrue(state.allowsAdditionalInfo());
                assertFalse(state.allowsFeedback());
                assertTrue(state.allowsPriorityChange());
            }
            case ASSIGNED -> {
                assertFalse(state.allowsInvestigationNotes());
                assertTrue(state.allowsAdditionalInfo());
                assertFalse(state.allowsFeedback());
                assertTrue(state.allowsPriorityChange());
            }
            case ACKNOWLEDGED -> {
                assertTrue(state.allowsInvestigationNotes());
                assertTrue(state.allowsAdditionalInfo());
                assertFalse(state.allowsFeedback());
                assertTrue(state.allowsPriorityChange());
            }
            case IN_PROGRESS -> {
                assertTrue(state.allowsInvestigationNotes());
                assertTrue(state.allowsAdditionalInfo());
                assertFalse(state.allowsFeedback());
                assertTrue(state.allowsPriorityChange());
            }
            case WAITING_FOR_USER -> {
                assertTrue(state.allowsInvestigationNotes());
                assertTrue(state.allowsAdditionalInfo());
                assertFalse(state.allowsFeedback());
                assertTrue(state.allowsPriorityChange());
            }
            case RESOLVED -> {
                assertFalse(state.allowsInvestigationNotes());
                assertFalse(state.allowsAdditionalInfo());
                assertTrue(state.allowsFeedback());
                assertFalse(state.allowsPriorityChange());
            }
            case VERIFIED -> {
                assertFalse(state.allowsInvestigationNotes());
                assertFalse(state.allowsAdditionalInfo());
                assertTrue(state.allowsFeedback());
                assertFalse(state.allowsPriorityChange());
            }
            case CLOSED -> {
                assertFalse(state.allowsInvestigationNotes());
                assertFalse(state.allowsAdditionalInfo());
                assertTrue(state.allowsFeedback());
                assertFalse(state.allowsPriorityChange());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(IncidentStatus.class)
    @DisplayName("IncidentStates.of is total and status() round-trips")
    void of_allStatuses_roundTripsCorrectly(IncidentStatus status) {
        IncidentState state = IncidentStates.of(status);
        assertNotNull(state);
        assertEquals(status, state.status());
        assertSame(state, IncidentStates.of(status), "States should be cached singletons");
    }

    @Test
    @DisplayName("IncidentStates.initial returns NEW state")
    void initial_whenCalled_returnsNewState() {
        IncidentState state = IncidentStates.initial();
        assertNotNull(state);
        assertEquals(IncidentStatus.NEW, state.status());
    }

    @Test
    @DisplayName("IncidentStates.of throws InvalidInputException for null status")
    void of_nullStatus_throwsInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> IncidentStates.of(null));
    }
}
