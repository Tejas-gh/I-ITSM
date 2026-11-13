package com.iitsm.api;

/**
 * HTTP status per error code, exactly as in CONTRACT_FREEZE.md §U.1:
 * INVALID_INPUT 400; INVALID_LOGIN 401; UNAUTHORIZED_OPERATION 403; *_NOT_FOUND 404;
 * INVALID_STATE_TRANSITION, INVALID_INCIDENT, TECHNICIAN_UNAVAILABLE, TECHNICIAN_OVERLOADED,
 * DUPLICATE_INCIDENT 409; MISSING_SLA 500. Plain Java: no framework types.
 */
final class ApiErrorStatus {

    static final int INTERNAL_SERVER_ERROR = 500;

    private ApiErrorStatus() {
    }

    static int httpStatusFor(String code) {
        if (code == null) {
            return INTERNAL_SERVER_ERROR;
        }
        if (code.endsWith("_NOT_FOUND")) {
            return 404;
        }
        return switch (code) {
            case "INVALID_INPUT" -> 400;
            case "INVALID_LOGIN" -> 401;
            case "UNAUTHORIZED_OPERATION" -> 403;
            case "INVALID_STATE_TRANSITION", "INVALID_INCIDENT", "TECHNICIAN_UNAVAILABLE",
                 "TECHNICIAN_OVERLOADED", "DUPLICATE_INCIDENT" -> 409;
            case "MISSING_SLA" -> 500;
            default -> INTERNAL_SERVER_ERROR;
        };
    }
}
