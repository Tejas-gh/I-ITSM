package com.iitsm.application.service;

import java.util.List;

import com.iitsm.domain.assignment.SwitchableAssignmentStrategy;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.repository.UserRepository;

/**
 * Administrative selection of the active technician-assignment strategy. The choice feeds the
 * {@link SwitchableAssignmentStrategy} that {@code IncidentService} uses, so it takes effect on the next automatic
 * assignment. The selection is held in memory. Restricted to administrators.
 */
public final class AssignmentStrategyAdminService {

    /** The active strategy and every strategy that can be selected. */
    public record Selection(String selected, List<String> available) {
    }

    private final UserRepository users;
    private final AdminPermissionPolicy permissionPolicy;
    private final SwitchableAssignmentStrategy strategy;

    public AssignmentStrategyAdminService(UserRepository users, AdminPermissionPolicy permissionPolicy,
                                          SwitchableAssignmentStrategy strategy) {
        if (users == null || permissionPolicy == null || strategy == null) {
            throw new InvalidInputException("users, permissionPolicy and strategy must not be null");
        }
        this.users = users;
        this.permissionPolicy = permissionPolicy;
        this.strategy = strategy;
    }

    public Selection getSelection(String actorId) {
        permissionPolicy.check(actorId, users);
        return current();
    }

    /**
     * Selects the strategy registered under {@code strategyName}.
     *
     * @throws InvalidInputException for a blank or unknown name (the active strategy is left unchanged)
     */
    public Selection select(String actorId, String strategyName) {
        permissionPolicy.check(actorId, users);
        strategy.select(strategyName);
        return current();
    }

    private Selection current() {
        return new Selection(strategy.selectedName(), strategy.availableNames());
    }
}
