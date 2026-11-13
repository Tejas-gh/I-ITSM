package com.iitsm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.iitsm.domain.exception.CategoryNotFoundException;
import com.iitsm.domain.exception.DuplicateIncidentException;
import com.iitsm.domain.exception.IncidentNotFoundException;
import com.iitsm.domain.exception.InvalidIncidentException;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.InvalidLoginException;
import com.iitsm.domain.exception.InvalidStateTransitionException;
import com.iitsm.domain.exception.ItsmException;
import com.iitsm.domain.exception.MissingSlaException;
import com.iitsm.domain.exception.ServiceNotFoundException;
import com.iitsm.domain.exception.TechnicianOverloadedException;
import com.iitsm.domain.exception.TechnicianUnavailableException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.exception.UserNotFoundException;
import com.iitsm.domain.model.IncidentStatus;

/** The code to HTTP status table of CONTRACT_FREEZE.md §U.1, checked against the real exception classes. */
class ApiErrorStatusTest {

    @ParameterizedTest
    @CsvSource({
            "INVALID_INPUT,400",
            "INVALID_LOGIN,401",
            "UNAUTHORIZED_OPERATION,403",
            "INCIDENT_NOT_FOUND,404",
            "SERVICE_NOT_FOUND,404",
            "USER_NOT_FOUND,404",
            "CATEGORY_NOT_FOUND,404",
            "INVALID_STATE_TRANSITION,409",
            "INVALID_INCIDENT,409",
            "TECHNICIAN_UNAVAILABLE,409",
            "TECHNICIAN_OVERLOADED,409",
            "DUPLICATE_INCIDENT,409",
            "MISSING_SLA,500"
    })
    void httpStatusFor_contractCode_returnsContractStatus(String code, int status) {
        assertEquals(status, ApiErrorStatus.httpStatusFor(code));
    }

    @Test
    void httpStatusFor_unknownOrNullCode_isServerError() {
        assertEquals(500, ApiErrorStatus.httpStatusFor("SOMETHING_NEW"));
        assertEquals(500, ApiErrorStatus.httpStatusFor(null));
    }

    @Test
    void httpStatusFor_everyDomainExceptionClass_matchesItsContractStatus() {
        assertStatus(400, new InvalidInputException("x"));
        assertStatus(401, new InvalidLoginException("x"));
        assertStatus(403, new UnauthorizedOperationException("x"));
        assertStatus(404, new IncidentNotFoundException("x"));
        assertStatus(404, new ServiceNotFoundException("x"));
        assertStatus(404, new UserNotFoundException("x"));
        assertStatus(404, new CategoryNotFoundException("x"));
        assertStatus(409, new InvalidStateTransitionException(IncidentStatus.NEW, "close"));
        assertStatus(409, new InvalidIncidentException("x"));
        assertStatus(409, new TechnicianUnavailableException("x"));
        assertStatus(409, new TechnicianOverloadedException("x"));
        assertStatus(409, new DuplicateIncidentException("x"));
        assertStatus(500, new MissingSlaException("x"));
    }

    private static void assertStatus(int expected, ItsmException exception) {
        assertEquals(expected, ApiErrorStatus.httpStatusFor(exception.getCode()), exception.getCode());
    }
}
