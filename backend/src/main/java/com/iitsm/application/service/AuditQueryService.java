package com.iitsm.application.service;

import com.iitsm.domain.audit.AuditEntry;
import com.iitsm.domain.audit.AuditRepository;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.repository.UserRepository;

import java.util.List;

/**
 * Application service for querying audit trails. Restricted to administrators.
 */
public final class AuditQueryService {

    private final AuditRepository auditRepository;
    private final UserRepository userRepository;
    private final AdminPermissionPolicy permissionPolicy;

    public AuditQueryService(AuditRepository auditRepository,
                             UserRepository userRepository,
                             AdminPermissionPolicy permissionPolicy) {
        if (auditRepository == null) {
            throw new InvalidInputException("auditRepository must not be null");
        }
        if (userRepository == null) {
            throw new InvalidInputException("userRepository must not be null");
        }
        if (permissionPolicy == null) {
            throw new InvalidInputException("permissionPolicy must not be null");
        }
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
        this.permissionPolicy = permissionPolicy;
    }

    /**
     * Retrieves the audit trail for a specific incident. Restricted to administrators.
     */
    public List<AuditEntry> findByIncidentId(String actorId, String incidentId) {
        permissionPolicy.check(actorId, userRepository);
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be blank");
        }
        return auditRepository.findByIncidentId(incidentId);
    }

    /**
     * Retrieves all recorded audit entries across the system. Restricted to administrators.
     */
    public List<AuditEntry> findAll(String actorId) {
        permissionPolicy.check(actorId, userRepository);
        return auditRepository.findAll();
    }
}
