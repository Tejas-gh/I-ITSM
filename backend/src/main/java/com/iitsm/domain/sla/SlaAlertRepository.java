package com.iitsm.domain.sla;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for persisting and querying {@link SlaAlert} entities.
 *
 * <p>Contract guarantees:
 * <ul>
 *   <li>Idempotent saves: Saving an alert with the same {@code (incidentId, slaType, kind)}
 *       performs an upsert without creating duplicate rows.</li>
 *   <li>Fast existence checks: {@link #exists(String, SlaType, SlaAlertKind)} allows the
 *       SLA monitor to safely skip previously raised alerts on repeated scheduler ticks.</li>
 *   <li>Querying by kind: Facilitates separating active warnings from recorded SLA violations.</li>
 * </ul>
 * </p>
 */
public interface SlaAlertRepository {

    /**
     * Persists or updates the given alert.
     *
     * @param alert the SLA alert to save; must not be null
     */
    void save(SlaAlert alert);

    /**
     * Checks whether an alert for the given incident, SLA type, and kind already exists.
     *
     * @param incidentId the incident identifier
     * @param slaType the SLA type (RESPONSE or RESOLUTION)
     * @param kind the alert kind (WARNING or VIOLATION)
     * @return true if a matching alert has already been saved, false otherwise
     */
    boolean exists(String incidentId, SlaType slaType, SlaAlertKind kind);

    /**
     * Retrieves an alert matching the given idempotency tuple, if present.
     *
     * @param incidentId the incident identifier
     * @param slaType the SLA type
     * @param kind the alert kind
     * @return optional containing the alert if found
     */
    Optional<SlaAlert> findByKey(String incidentId, SlaType slaType, SlaAlertKind kind);

    /**
     * Retrieves an alert by its unique identifier.
     *
     * @param id the unique alert identifier
     * @return optional containing the alert if found
     */
    Optional<SlaAlert> findById(String id);

    /**
     * Returns all recorded SLA alerts in insertion order.
     *
     * @return unmodifiable list of all alerts
     */
    List<SlaAlert> findAll();

    /**
     * Returns all SLA alerts of the given kind (WARNING or VIOLATION).
     *
     * @param kind the alert kind to filter by; must not be null
     * @return unmodifiable list of matching alerts
     */
    List<SlaAlert> findByKind(SlaAlertKind kind);

    /**
     * Returns all SLA alerts associated with the specified incident.
     *
     * @param incidentId the incident identifier; must not be blank
     * @return unmodifiable list of matching alerts
     */
    List<SlaAlert> findByIncidentId(String incidentId);
}
