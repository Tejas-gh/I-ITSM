package com.iitsm.domain.assignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.Technician;

/**
 * An {@link AssignmentStrategy} that delegates to one of several registered strategies, so the active
 * algorithm can be changed at runtime without touching its callers (CONTRACT_FREEZE.md §F.2).
 *
 * <p>The selection is kept in memory and is safe to change while other threads are ranking.</p>
 */
public final class SwitchableAssignmentStrategy implements AssignmentStrategy {

    private final Map<String, AssignmentStrategy> strategiesByName;
    private volatile AssignmentStrategy selected;

    public SwitchableAssignmentStrategy(List<AssignmentStrategy> strategies, String initialName) {
        if (strategies == null || strategies.isEmpty()) {
            throw new InvalidInputException("At least one assignment strategy is required");
        }
        Map<String, AssignmentStrategy> byName = new LinkedHashMap<>();
        for (AssignmentStrategy strategy : strategies) {
            if (strategy == null) {
                throw new InvalidInputException("strategies must not contain null");
            }
            if (byName.putIfAbsent(strategy.name(), strategy) != null) {
                throw new InvalidInputException("Duplicate assignment strategy name: " + strategy.name());
            }
        }
        this.strategiesByName = Collections.unmodifiableMap(byName);
        this.selected = lookup(initialName);
    }

    /** Makes the strategy registered under {@code name} the active one. */
    public void select(String name) {
        this.selected = lookup(name);
    }

    public String selectedName() {
        return selected.name();
    }

    /** Names of all registered strategies, in registration order. */
    public List<String> availableNames() {
        return Collections.unmodifiableList(new ArrayList<>(strategiesByName.keySet()));
    }

    /** Name of the currently active delegate (used in assignment-failure descriptions). */
    @Override
    public String name() {
        return selected.name();
    }

    @Override
    public List<TechnicianScore> rank(Incident incident,
                                      List<Technician> candidates,
                                      Map<String, Integer> activeWorkloadByTechnicianId) {
        return selected.rank(incident, candidates, activeWorkloadByTechnicianId);
    }

    private AssignmentStrategy lookup(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("Assignment strategy name must not be blank");
        }
        AssignmentStrategy strategy = strategiesByName.get(name);
        if (strategy == null) {
            throw new InvalidInputException("Unknown assignment strategy '" + name + "'; available: "
                    + String.join(", ", strategiesByName.keySet()));
        }
        return strategy;
    }
}
