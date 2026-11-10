package com.iitsm.domain.model;

/** Role of a user in the system; TEAM_LEAD and IT_MANAGER are used by escalation in later phases. */
public enum Role {
    EMPLOYEE,
    TECHNICIAN,
    TEAM_LEAD,
    ADMINISTRATOR,
    IT_MANAGER
}
