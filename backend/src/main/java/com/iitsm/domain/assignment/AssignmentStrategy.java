package com.iitsm.domain.assignment;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.Technician;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AssignmentStrategy {

    String name();                                                         // e.g. "skill-based"

    List<TechnicianScore> rank(Incident incident,
                               List<Technician> candidates,
                               Map<String, Integer> activeWorkloadByTechnicianId);   // best first; may omit unsuitable technicians

    default Optional<Technician> pick(Incident incident, List<Technician> candidates,
                                      Map<String, Integer> activeWorkloadByTechnicianId) {
        return rank(incident, candidates, activeWorkloadByTechnicianId).stream().findFirst().map(TechnicianScore::technician);
    }
}
