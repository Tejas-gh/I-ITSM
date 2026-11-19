package com.iitsm.domain.escalation;

import java.util.Objects;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Role;

/**
 * Enumeration of incident escalation levels and their assigned target roles in the
 * Chain of Responsibility.
 *
 * <p>Levels:
 * <ul>
 *   <li>Level 1: Team Lead (breached response deadline)</li>
 *   <li>Level 2: Administrator (breached response deadline + 1 response target)</li>
 *   <li>Level 3: IT Manager (breached response deadline + 2 response targets)</li>
 * </ul>
 * </p>
 */
public enum EscalationLevel {

    LEVEL_1(1, Role.TEAM_LEAD, "Team Lead"),
    LEVEL_2(2, Role.ADMINISTRATOR, "Administrator"),
    LEVEL_3(3, Role.IT_MANAGER, "IT Manager");

    private final int level;
    private final Role targetRole;
    private final String roleTitle;

    EscalationLevel(int level, Role targetRole, String roleTitle) {
        this.level = level;
        this.targetRole = Objects.requireNonNull(targetRole, "targetRole must not be null");
        this.roleTitle = Objects.requireNonNull(roleTitle, "roleTitle must not be null");
    }

    public int getLevel() {
        return level;
    }

    public Role getTargetRole() {
        return targetRole;
    }

    public String getRoleTitle() {
        return roleTitle;
    }

    public static EscalationLevel fromInt(int level) {
        for (EscalationLevel el : values()) {
            if (el.level == level) {
                return el;
            }
        }
        throw new InvalidInputException("Invalid escalation level: " + level + " (expected 1, 2, or 3)");
    }
}
