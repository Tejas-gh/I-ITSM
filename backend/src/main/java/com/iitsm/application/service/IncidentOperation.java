package com.iitsm.application.service;

/**
 * Operations on incidents subject to role-based permission checks.
 */
public enum IncidentOperation {
    CREATE,
    VIEW,
    ASSIGN,
    ACKNOWLEDGE,
    START_WORK,
    ADD_INVESTIGATION_NOTE,
    REQUEST_INFO,
    RESPOND_INFO,
    ADD_ADDITIONAL_INFO,
    RESOLVE,
    VERIFY,
    REJECT_RESOLUTION,
    CLOSE,
    SUBMIT_FEEDBACK,
    UPDATE_PRIORITY,
    SUGGEST_CATEGORY
}
