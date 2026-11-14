package com.iitsm.api;

import com.iitsm.api.dto.AuditEntryResponse;
import com.iitsm.application.service.AuditQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for querying audit log entries.
 * Restricted to administrators; actor id is derived from authentication.
 */
@RestController
@RequestMapping("/api/audit")
@Transactional
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/incidents/{incidentId}")
    @Transactional(readOnly = true)
    public List<AuditEntryResponse> getAuditByIncidentId(@PathVariable String incidentId,
                                                         Authentication authentication) {
        return auditQueryService.findByIncidentId(authentication.getName(), incidentId)
                .stream()
                .map(AuditEntryResponse::from)
                .toList();
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AuditEntryResponse> getAllAudit(Authentication authentication) {
        return auditQueryService.findAll(authentication.getName())
                .stream()
                .map(AuditEntryResponse::from)
                .toList();
    }
}
