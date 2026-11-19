package com.iitsm.api;

import java.util.List;
import java.util.Objects;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iitsm.api.dto.EscalationResponse;
import com.iitsm.application.service.AdminPermissionPolicy;
import com.iitsm.application.service.EscalationService;
import com.iitsm.domain.escalation.EscalationRecord;
import com.iitsm.domain.repository.UserRepository;

/**
 * Controller exposing endpoints for incident escalations:
 * <ul>
 *   <li>GET /api/incidents/{id}/escalations - Get escalations for an incident</li>
 *   <li>GET /api/escalations - List all recorded escalations</li>
 *   <li>POST /api/escalations/run - Trigger manual escalation evaluation (Admin only)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class EscalationController {

    private final EscalationService escalationService;
    private final UserRepository users;
    private final AdminPermissionPolicy permissionPolicy;

    public EscalationController(EscalationService escalationService,
                                UserRepository users,
                                AdminPermissionPolicy permissionPolicy) {
        this.escalationService = Objects.requireNonNull(escalationService, "escalationService must not be null");
        this.users = Objects.requireNonNull(users, "users must not be null");
        this.permissionPolicy = Objects.requireNonNull(permissionPolicy, "permissionPolicy must not be null");
    }

    @GetMapping("/incidents/{id}/escalations")
    public List<EscalationResponse> getIncidentEscalations(@PathVariable("id") String incidentId) {
        return escalationService.getEscalationsForIncident(incidentId).stream()
                .map(EscalationResponse::from)
                .toList();
    }

    @GetMapping("/escalations")
    public List<EscalationResponse> getAllEscalations() {
        return escalationService.getAllEscalations().stream()
                .map(EscalationResponse::from)
                .toList();
    }

    @PostMapping("/escalations/run")
    public List<EscalationResponse> runEscalations(Authentication authentication) {
        String actorId = authentication.getName();
        permissionPolicy.check(actorId, users);

        List<EscalationRecord> triggered = escalationService.runEscalations();
        return triggered.stream()
                .map(EscalationResponse::from)
                .toList();
    }
}
