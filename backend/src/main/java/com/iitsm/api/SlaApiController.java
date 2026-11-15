package com.iitsm.api;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.iitsm.api.dto.SlaAlertResponse;
import com.iitsm.api.dto.SlaMonitorRunResponse;
import com.iitsm.application.service.AdminPermissionPolicy;
import com.iitsm.application.service.SlaMonitorResult;
import com.iitsm.application.service.SlaMonitorService;
import com.iitsm.domain.repository.UserRepository;

/**
 * Controller exposing SLA monitoring endpoints:
 * <ul>
 *   <li>POST /api/sla/monitor/run - Manual trigger by administrators</li>
 *   <li>GET /api/sla/violations - Query recorded SLA breach violations</li>
 *   <li>GET /api/sla/at-risk - Query active warnings/at-risk incidents</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/sla")
public class SlaApiController {

    private final SlaMonitorService monitorService;
    private final UserRepository users;
    private final AdminPermissionPolicy permissionPolicy;
    private final Clock clock;

    public SlaApiController(SlaMonitorService monitorService,
                            UserRepository users,
                            AdminPermissionPolicy permissionPolicy,
                            Clock clock) {
        this.monitorService = Objects.requireNonNull(monitorService, "monitorService must not be null");
        this.users = Objects.requireNonNull(users, "users must not be null");
        this.permissionPolicy = Objects.requireNonNull(permissionPolicy, "permissionPolicy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Triggers a manual monitor run. Restricted to administrators.
     */
    @PostMapping("/monitor/run")
    public SlaMonitorRunResponse runMonitor(Authentication authentication) {
        String actorId = authentication.getName();
        permissionPolicy.check(actorId, users);

        Instant now = clock.instant();
        SlaMonitorResult result = monitorService.runMonitor(now);
        return SlaMonitorRunResponse.from(result, now);
    }

    /**
     * Lists all recorded SLA breach violations, optionally filtered by incident.
     */
    @GetMapping("/violations")
    @Transactional(readOnly = true)
    public List<SlaAlertResponse> getViolations(@RequestParam(name = "incidentId", required = false) String incidentId) {
        var alerts = monitorService.getViolations();
        if (incidentId != null && !incidentId.isBlank()) {
            alerts = alerts.stream()
                    .filter(a -> incidentId.equals(a.incidentId()))
                    .toList();
        }
        return alerts.stream()
                .map(SlaAlertResponse::from)
                .toList();
    }

    /**
     * Lists all active SLA warning/at-risk alerts, optionally filtered by incident.
     */
    @GetMapping("/at-risk")
    @Transactional(readOnly = true)
    public List<SlaAlertResponse> getAtRisk(@RequestParam(name = "incidentId", required = false) String incidentId) {
        var alerts = monitorService.getAtRiskAlerts();
        if (incidentId != null && !incidentId.isBlank()) {
            alerts = alerts.stream()
                    .filter(a -> incidentId.equals(a.incidentId()))
                    .toList();
        }
        return alerts.stream()
                .map(SlaAlertResponse::from)
                .toList();
    }
}
