package com.iitsm.domain.escalation;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for managing persistent or in-memory escalation records.
 *
 * <p>Serves as the frozen specification contract for Gate B6 and Developer A's
 * Gate A7 JPA persistence.</p>
 */
public interface EscalationRepository {

    /**
     * Persists or upserts an escalation record.
     *
     * @param record the record to save
     * @return the saved record with its identifier assigned
     */
    EscalationRecord save(EscalationRecord record);

    /**
     * Retrieves all escalation records recorded for a given incident in chronological order.
     *
     * @param incidentId the incident id
     * @return list of escalation records
     */
    List<EscalationRecord> findByIncidentId(String incidentId);

    /**
     * Retrieves the latest escalation record for an incident, if any.
     *
     * @param incidentId the incident id
     * @return the latest escalation record, or empty
     */
    Optional<EscalationRecord> findLatestByIncidentId(String incidentId);

    /**
     * Checks whether an escalation of a specific level has already been recorded for an incident.
     * Used as an idempotency guard.
     *
     * @param incidentId the incident id
     * @param level      the escalation level
     * @return true if already recorded
     */
    boolean existsByIncidentIdAndLevel(String incidentId, int level);

    /**
     * Retrieves all escalation records across all incidents.
     *
     * @return list of all records
     */
    List<EscalationRecord> findAll();
}
