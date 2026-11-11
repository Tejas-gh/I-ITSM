package com.iitsm.application.report;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.exception.UserNotFoundException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.repository.ItServiceRepository;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.domain.sla.SlaStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Application service providing dashboard and analytical report queries.
 */
public final class DashboardService {

    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final ItServiceRepository serviceRepository;
    private final SlaEvaluator slaEvaluator;
    private final Clock clock;

    public DashboardService(IncidentRepository incidentRepository,
                            UserRepository userRepository,
                            ItServiceRepository serviceRepository,
                            SlaEvaluator slaEvaluator,
                            Clock clock) {
        if (incidentRepository == null) {
            throw new InvalidInputException("incidentRepository must not be null");
        }
        if (userRepository == null) {
            throw new InvalidInputException("userRepository must not be null");
        }
        if (serviceRepository == null) {
            throw new InvalidInputException("serviceRepository must not be null");
        }
        if (slaEvaluator == null) {
            throw new InvalidInputException("slaEvaluator must not be null");
        }
        if (clock == null) {
            throw new InvalidInputException("clock must not be null");
        }
        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.slaEvaluator = slaEvaluator;
        this.clock = clock;
    }

    private void checkAdminAccess(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + actorId));

        if (!actor.isActive()) {
            throw new UnauthorizedOperationException("User is inactive: " + actorId);
        }

        Role role = actor.getRole();
        if (role != Role.ADMINISTRATOR && role != Role.IT_MANAGER) {
            throw new UnauthorizedOperationException("Only administrators can view dashboard reports");
        }
    }

    /**
     * Calculates the high-level incident overview statistics.
     */
    public IncidentOverview getOverview(String actorId) {
        checkAdminAccess(actorId);

        List<Incident> all = incidentRepository.findAll();
        Instant now = clock.instant();

        long total = all.size();
        long openCount = 0;
        long criticalCount = 0;
        long resolvedCount = 0;
        long breachedCount = 0;

        for (Incident inc : all) {
            IncidentStatus status = inc.getStatus();
            if (status != IncidentStatus.RESOLVED && status != IncidentStatus.VERIFIED && status != IncidentStatus.CLOSED) {
                openCount++;
            } else {
                resolvedCount++;
            }

            if (inc.getPriority() == PriorityLevel.P1) {
                criticalCount++;
            }

            if (inc.getResponseDeadline().isPresent() && inc.getResolutionDeadline().isPresent()) {
                if (slaEvaluator.evaluate(inc, now).overall() == SlaStatus.BREACHED) {
                    breachedCount++;
                }
            }
        }

        return new IncidentOverview(total, openCount, criticalCount, resolvedCount, breachedCount);
    }

    /**
     * Calculates workload and capacity allocation metrics for all technicians.
     */
    public List<TechnicianWorkloadEntry> getTechnicianWorkloads(String actorId) {
        checkAdminAccess(actorId);

        List<Technician> technicians = userRepository.findAllTechnicians();
        List<TechnicianWorkloadEntry> result = new ArrayList<>();

        for (Technician tech : technicians) {
            int active = incidentRepository.countActiveByTechnicianId(tech.getId());
            int max = tech.getMaxActiveIncidents();
            double utilization = max > 0 ? (double) active / max : 0.0;
            result.add(new TechnicianWorkloadEntry(
                    tech.getId(),
                    tech.getName(),
                    active,
                    max,
                    utilization
            ));
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Calculates the incident distribution count for each IT service.
     */
    public List<ServiceIncidentCount> getServiceIncidentCounts(String actorId) {
        checkAdminAccess(actorId);

        List<ItService> services = serviceRepository.findAll();
        List<Incident> allIncidents = incidentRepository.findAll();
        List<ServiceIncidentCount> result = new ArrayList<>();

        for (ItService srv : services) {
            long count = 0;
            for (Incident inc : allIncidents) {
                if (inc.getService().getId().equals(srv.getId())) {
                    count++;
                }
            }
            result.add(new ServiceIncidentCount(srv.getId(), srv.getName(), count));
        }

        return Collections.unmodifiableList(result);
    }
}
