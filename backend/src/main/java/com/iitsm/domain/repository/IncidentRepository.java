package com.iitsm.domain.repository;

import com.iitsm.domain.incident.Incident;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository {

    Incident save(Incident incident);

    Optional<Incident> findById(String id);

    List<Incident> findAll();                                  // insertion order

    List<Incident> findByReporterId(String reporterId);

    List<Incident> findByAssignedTechnicianId(String technicianId);

    int countActiveByTechnicianId(String technicianId);        // assigned technician matches && status.countsTowardWorkload()

    List<Incident> search(IncidentSearchCriteria criteria);    // AND of non-null criteria
}
