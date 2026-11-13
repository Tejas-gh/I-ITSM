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
import com.iitsm.infrastructure.persistence.memory.InMemoryAuditRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryCategoryRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryIncidentRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryItServiceRepository;
import com.iitsm.infrastructure.persistence.memory.InMemorySlaPolicyRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReportingAdminConfigTest {

    @Test
    void testBeanWiring() {
        ReportingAdminConfig config = new ReportingAdminConfig();

        AuditRepository auditRepo = new InMemoryAuditRepository();
        IncidentRepository incidentRepo = new InMemoryIncidentRepository();
        UserRepository userRepo = new InMemoryUserRepository();
        ItServiceRepository serviceRepo = new InMemoryItServiceRepository();
        CategoryRepository catRepo = new InMemoryCategoryRepository();
        SlaPolicyRepository slaRepo = new InMemorySlaPolicyRepository();
        SlaEvaluator slaEvaluator = new SlaEvaluator();
        Clock clock = Clock.systemUTC();

        AdminPermissionPolicy policy = config.adminPermissionPolicy();
        assertNotNull(policy);

        ItServiceAdminService itServiceAdminService = config.itServiceAdminService(serviceRepo, userRepo, policy);
        assertNotNull(itServiceAdminService);

        CategoryAdminService categoryAdminService = config.categoryAdminService(catRepo, userRepo, policy);
        assertNotNull(categoryAdminService);

        SlaPolicyAdminService slaPolicyAdminService = config.slaPolicyAdminService(slaRepo, serviceRepo, userRepo, policy);
        assertNotNull(slaPolicyAdminService);

        AuditQueryService auditQueryService = config.auditQueryService(auditRepo, userRepo, policy);
        assertNotNull(auditQueryService);

        DashboardService dashboardService = config.dashboardService(incidentRepo, userRepo, serviceRepo, slaEvaluator, clock);
        assertNotNull(dashboardService);
    }
}
