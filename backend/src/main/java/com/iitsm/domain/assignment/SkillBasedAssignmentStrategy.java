package com.iitsm.domain.assignment;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.Technician;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Ranks technicians by their proficiency in the incident's category; technicians
 * without that skill, or who cannot take work right now, are omitted.
 *
 * <p>Ordering (best first): higher skill level, then fewer active incidents, then more
 * years of experience, then technician id (so the result is deterministic). The score of
 * each entry is the technician's skill level for the incident's category (1..5).</p>
 */
public final class SkillBasedAssignmentStrategy implements AssignmentStrategy {

    private static final String NAME = "skill-based";

    private static final Comparator<Ranked> ORDER = (a, b) -> {
        int result = Integer.compare(b.skill(), a.skill());
        if (result != 0) {
            return result;
        }
        result = Integer.compare(a.activeIncidents(), b.activeIncidents());
        if (result != 0) {
            return result;
        }
        result = Integer.compare(b.technician().getYearsOfExperience(),
                a.technician().getYearsOfExperience());
        if (result != 0) {
            return result;
        }
        return a.technician().getId().compareTo(b.technician().getId());
    };

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<TechnicianScore> rank(Incident incident,
                                      List<Technician> candidates,
                                      Map<String, Integer> activeWorkloadByTechnicianId) {
        if (incident == null) {
            throw new InvalidInputException("incident must not be null");
        }
        if (candidates == null) {
            throw new InvalidInputException("candidates must not be null");
        }
        if (activeWorkloadByTechnicianId == null) {
            throw new InvalidInputException("activeWorkloadByTechnicianId must not be null");
        }

        String categoryId = incident.getCategory().getId();
        List<Ranked> ranked = new ArrayList<>();
        for (Technician technician : candidates) {
            if (technician == null) {
                throw new InvalidInputException("candidates must not contain null");
            }
            int active = activeIncidentsOf(technician, activeWorkloadByTechnicianId);
            if (!technician.isAssignable() || technician.isOverloaded(active)) {
                continue;
            }
            int skill = technician.skillLevelFor(categoryId);
            if (skill == 0) {
                continue;
            }
            ranked.add(new Ranked(technician, active, skill));
        }
        ranked.sort(ORDER);

        List<TechnicianScore> result = new ArrayList<>(ranked.size());
        for (Ranked entry : ranked) {
            String reason = "Skill level " + entry.skill() + " in " + categoryId
                    + "; " + entry.activeIncidents() + " active incident(s)";
            result.add(new TechnicianScore(entry.technician(), entry.skill(), reason));
        }
        return Collections.unmodifiableList(result);
    }

    private static int activeIncidentsOf(Technician technician, Map<String, Integer> workload) {
        Integer active = workload.get(technician.getId());
        if (active == null) {
            throw new InvalidInputException("No workload entry for technician " + technician.getId());
        }
        if (active < 0) {
            throw new InvalidInputException("Workload must not be negative for technician " + technician.getId());
        }
        return active;
    }

    private record Ranked(Technician technician, int activeIncidents, int skill) {
    }
}
