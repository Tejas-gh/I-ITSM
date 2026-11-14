package com.iitsm.config;

import com.iitsm.application.report.DashboardService;
import com.iitsm.application.service.AdminPermissionPolicy;
import com.iitsm.application.service.AuditQueryService;
import com.iitsm.application.service.CategoryAdminService;
import com.iitsm.application.service.ItServiceAdminService;
import com.iitsm.application.service.SlaPolicyAdminService;
import com.iitsm.domain.audit.AuditRepository;
import com.iitsm.domain.repository.CategoryRepository;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.repository.ItServiceRepository;
import com.iitsm.domain.repository.SlaPolicyRepository;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.domain.sla.SlaEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Gate B6: Configuration wiring reporting, audit queries, and catalog administrative services.
 */
@Configuration
public class ReportingAdminConfig {

    @Bean
    public AdminPermissionPolicy adminPermissionPolicy() {
        return new AdminPermissionPolicy();
    }

    @Bean
    public ItServiceAdminService itServiceAdminService(ItServiceRepository itServiceRepository,
                                                       UserRepository userRepository,
                                                       AdminPermissionPolicy adminPermissionPolicy) {
        return new ItServiceAdminService(itServiceRepository, userRepository, adminPermissionPolicy);
    }

    @Bean
    public CategoryAdminService categoryAdminService(CategoryRepository categoryRepository,
                                                     UserRepository userRepository,
                                                     AdminPermissionPolicy adminPermissionPolicy) {
        return new CategoryAdminService(categoryRepository, userRepository, adminPermissionPolicy);
    }

    @Bean
    public SlaPolicyAdminService slaPolicyAdminService(SlaPolicyRepository slaPolicyRepository,
                                                       ItServiceRepository itServiceRepository,
                                                       UserRepository userRepository,
                                                       AdminPermissionPolicy adminPermissionPolicy) {
        return new SlaPolicyAdminService(slaPolicyRepository, itServiceRepository, userRepository, adminPermissionPolicy);
    }

    @Bean
    public AuditQueryService auditQueryService(AuditRepository auditRepository,
                                               UserRepository userRepository,
                                               AdminPermissionPolicy adminPermissionPolicy) {
        return new AuditQueryService(auditRepository, userRepository, adminPermissionPolicy);
    }

    @Bean
    public DashboardService dashboardService(IncidentRepository incidentRepository,
                                             UserRepository userRepository,
                                             ItServiceRepository itServiceRepository,
                                             SlaEvaluator slaEvaluator,
                                             Clock clock) {
        return new DashboardService(incidentRepository, userRepository, itServiceRepository, slaEvaluator, clock);
    }
}
