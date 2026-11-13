package com.iitsm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;

import com.iitsm.api.dto.ApiError;
import com.iitsm.domain.exception.IncidentNotFoundException;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.InvalidLoginException;
import com.iitsm.domain.exception.InvalidStateTransitionException;
import com.iitsm.domain.exception.MissingSlaException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.model.IncidentStatus;

class GlobalExceptionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-16T09:00:00Z");

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(Clock.fixed(NOW, ZoneOffset.UTC));

    private static ApiError body(ResponseEntity<ApiError> response) {
        return response.getBody();
    }

    @Test
    void domainException_invalidTransition_is409WithContractBody() {
        ResponseEntity<ApiError> response =
                handler.onDomainException(new InvalidStateTransitionException(IncidentStatus.NEW, "close"));

        assertEquals(409, response.getStatusCode().value());
        ApiError error = body(response);
        assertEquals(NOW, error.timestamp());
        assertEquals(409, error.status());
        assertEquals("INVALID_STATE_TRANSITION", error.code());
        assertEquals("Cannot close an incident that is NEW", error.message());
        assertEquals(List.of(), error.details());
    }

    @Test
    void domainException_eachCategory_usesItsStatusAndCode() {
        assertEquals(400, handler.onDomainException(new InvalidInputException("bad")).getStatusCode().value());
        assertEquals(401, handler.onDomainException(new InvalidLoginException("bad")).getStatusCode().value());
        ResponseEntity<ApiError> forbidden = handler.onDomainException(new UnauthorizedOperationException("no"));
        assertEquals(403, forbidden.getStatusCode().value());
        assertEquals("UNAUTHORIZED_OPERATION", body(forbidden).code());
        ResponseEntity<ApiError> notFound = handler.onDomainException(new IncidentNotFoundException("INC9"));
        assertEquals(404, notFound.getStatusCode().value());
        assertEquals("INCIDENT_NOT_FOUND", body(notFound).code());
        ResponseEntity<ApiError> sla = handler.onDomainException(new MissingSlaException("no policy"));
        assertEquals(500, sla.getStatusCode().value());
        assertEquals("MISSING_SLA", body(sla).code());
    }

    @Test
    void malformedRequest_is400InvalidInputWithoutEchoingTheCause() {
        ResponseEntity<ApiError> response =
                handler.onMalformedRequest(new IllegalArgumentException("Cannot deserialize at line 3 column 9"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("INVALID_INPUT", body(response).code());
        assertFalse(body(response).message().contains("line 3"));
    }

    @Test
    void unexpectedException_is500InternalErrorWithoutLeakingInternals() {
        ResponseEntity<Object> response =
                handler.onUnexpected(new IllegalStateException("jdbc:postgresql://secret-host password=hunter2"));

        assertEquals(500, response.getStatusCode().value());
        ApiError error = assertInstanceOf(ApiError.class, response.getBody());
        assertEquals("INTERNAL_ERROR", error.code());
        assertEquals(500, error.status());
        assertEquals(NOW, error.timestamp());
        assertEquals(List.of(), error.details());
        assertFalse(error.message().contains("secret-host"));
        assertFalse(error.message().contains("hunter2"));
    }

    @Test
    void springWebException_keepsItsOwnStatusInsteadOfBecoming500() {
        ResponseEntity<Object> response =
                handler.onUnexpected(new ErrorResponseException(HttpStatus.METHOD_NOT_ALLOWED));

        assertEquals(405, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof ProblemDetail);
    }
}
