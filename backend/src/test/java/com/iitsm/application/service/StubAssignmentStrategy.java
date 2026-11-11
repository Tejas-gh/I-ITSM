package com.iitsm.application.service;

import com.iitsm.domain.assignment.AssignmentStrategy;
import com.iitsm.domain.assignment.TechnicianScore;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.Technician;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class StubAssignmentStrategy implements AssignmentStrategy {

    private final String name;
    private Technician designatedTechnician;

    public StubAssignmentStrategy() {
        this("stub-strategy");
    }

    public StubAssignmentStrategy(String name) {
        this.name = name;
    }

    public void setDesignatedTechnician(Technician technician) {
        this.designatedTechnician = technician;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<TechnicianScore> rank(
            Incident incident,
            List<Technician> candidates,
            Map<String, Integer> activeWorkloadByTechnicianId
    ) {
        if (designatedTechnician != null && candidates.contains(designatedTechnician)) {
            return List.of(new TechnicianScore(designatedTechnician, 100.0, "Designated stub technician"));
        }
        if (!candidates.isEmpty()) {
            return List.of(new TechnicianScore(candidates.get(0), 50.0, "First available candidate"));
        }
        return Collections.emptyList();
    }
}
