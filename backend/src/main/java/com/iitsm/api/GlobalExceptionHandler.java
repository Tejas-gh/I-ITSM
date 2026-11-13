package com.iitsm.api;

import java.time.Clock;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.iitsm.api.dto.ApiError;
import com.iitsm.domain.exception.ItsmException;

/**
 * Single place where exceptions become HTTP responses, in the error format of CONTRACT_FREEZE.md §U.1.
 * Domain exceptions map by their error code; malformed requests are INVALID_INPUT; anything unexpected is a
 * generic INTERNAL_ERROR (details are logged, never returned).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String INVALID_INPUT = "INVALID_INPUT";
    static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private static final System.Logger LOGGER = System.getLogger(GlobalExceptionHandler.class.getName());

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ItsmException.class)
    public ResponseEntity<ApiError> onDomainException(ItsmException ex) {
        return body(ApiErrorStatus.httpStatusFor(ex.getCode()), ex.getCode(), ex.getMessage());
    }

    /** Malformed JSON, unknown enum values and unparsable request parameters. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> onMalformedRequest(Exception ex) {
        return body(HttpStatus.BAD_REQUEST.value(), INVALID_INPUT, "Malformed or invalid request");
    }

    /**
     * Last resort. Spring's own web exceptions (unknown route, wrong method, unsupported media type, ...) keep
     * the status and body Spring gives them, because CONTRACT_FREEZE.md defines no error code for them.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> onUnexpected(Exception ex) {
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            return ResponseEntity.status(springError.getStatusCode())
                    .headers(springError.getHeaders())
                    .body(springError.getBody());
        }
        LOGGER.log(System.Logger.Level.ERROR, "Unhandled exception while processing request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(HttpStatus.INTERNAL_SERVER_ERROR.value(), INTERNAL_ERROR,
                        "An unexpected error occurred"));
    }

    private ResponseEntity<ApiError> body(int status, String code, String message) {
        return ResponseEntity.status(status).body(error(status, code, message));
    }

    private ApiError error(int status, String code, String message) {
        return new ApiError(clock.instant(), status, code, message, List.of());
    }
}
