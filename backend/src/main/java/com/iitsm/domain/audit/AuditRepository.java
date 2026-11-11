package com.iitsm.domain.audit;

import java.util.List;

/**
 * Domain repository contract for persisting and retrieving audit entries.
 */
public interface AuditRepository {

    void save(AuditEntry entry);

    List<AuditEntry> findByIncidentId(String incidentId);

    List<AuditEntry> findAll();
}
