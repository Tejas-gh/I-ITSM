package com.iitsm.api;

import com.iitsm.api.dto.OverviewResponse;
import com.iitsm.api.dto.ServiceIncidentCountResponse;
import com.iitsm.api.dto.TechnicianWorkloadResponse;
import com.iitsm.application.report.DashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for analytical and operational dashboard reports.
 * Restricted to administrators; actor id is derived from authentication.
 */
@RestController
@RequestMapping("/api/dashboard")
@Transactional
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public OverviewResponse getOverview(Authentication authentication) {
        return OverviewResponse.from(dashboardService.getOverview(authentication.getName()));
    }

    @GetMapping("/technicians")
    @Transactional(readOnly = true)
    public List<TechnicianWorkloadResponse> getTechnicianWorkloads(Authentication authentication) {
        return dashboardService.getTechnicianWorkloads(authentication.getName())
                .stream()
                .map(TechnicianWorkloadResponse::from)
                .toList();
    }

    @GetMapping("/services")
    @Transactional(readOnly = true)
    public List<ServiceIncidentCountResponse> getServiceIncidentCounts(Authentication authentication) {
        return dashboardService.getServiceIncidentCounts(authentication.getName())
                .stream()
                .map(ServiceIncidentCountResponse::from)
                .toList();
    }
}
