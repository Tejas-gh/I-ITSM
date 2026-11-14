package com.iitsm.api;

import com.iitsm.api.dto.OverviewResponse;
import com.iitsm.api.dto.ServiceIncidentCountResponse;
import com.iitsm.api.dto.TechnicianWorkloadResponse;
import com.iitsm.application.report.DashboardService;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.repository.ItServiceRepository;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.infrastructure.persistence.memory.InMemoryIncidentRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryItServiceRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.TestClock;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DashboardControllerTest {

    private IncidentRepository incidentRepository;
    private UserRepository userRepository;
    private ItServiceRepository serviceRepository;
    private DashboardController controller;

    private static Authentication authFor(String userId) {
        return new TestingAuthenticationToken(userId, null);
    }

    private final Authentication adminAuth = authFor("ADMIN01");
    private final Authentication employeeAuth = authFor("EMP01");

    @BeforeEach
    void setUp() {
        incidentRepository = new InMemoryIncidentRepository();
        userRepository = new InMemoryUserRepository();
        serviceRepository = new InMemoryItServiceRepository();
        TestClock clock = new TestClock();
        SlaEvaluator slaEvaluator = new SlaEvaluator();

        DashboardService dashboardService = new DashboardService(
                incidentRepository, userRepository, serviceRepository, slaEvaluator, clock
        );
        controller = new DashboardController(dashboardService);

        Administrator admin = TestData.admin("ADMIN01");
        Employee employee = TestData.employee("EMP01");
        Technician tech = TestData.technician("TECH01", 5);
        ItService svc = TestData.service("SRV-PAYMENT", BusinessCriticality.CRITICAL);

        userRepository.save(admin);
        userRepository.save(employee);
        userRepository.save(tech);
        serviceRepository.save(svc);
    }

    @Test
    void getOverview_asAdmin_returnsMetrics() {
        OverviewResponse response = controller.getOverview(adminAuth);

        assertNotNull(response);
        assertEquals(0, response.totalIncidents());
        assertEquals(0, response.openIncidents());
    }

    @Test
    void getOverview_asEmployee_throwsUnauthorized() {
        assertThrows(UnauthorizedOperationException.class, () ->
                controller.getOverview(employeeAuth));
    }

    @Test
    void getTechnicianWorkloads_asAdmin_returnsWorkloadList() {
        List<TechnicianWorkloadResponse> list = controller.getTechnicianWorkloads(adminAuth);

        assertEquals(1, list.size());
        assertEquals("TECH01", list.get(0).technicianId());
        assertEquals(0, list.get(0).activeIncidents());
        assertEquals(5, list.get(0).maxActiveIncidents());
    }

    @Test
    void getTechnicianWorkloads_asEmployee_throwsUnauthorized() {
        assertThrows(UnauthorizedOperationException.class, () ->
                controller.getTechnicianWorkloads(employeeAuth));
    }

    @Test
    void getServiceIncidentCounts_asAdmin_returnsCounts() {
        List<ServiceIncidentCountResponse> list = controller.getServiceIncidentCounts(adminAuth);

        assertEquals(1, list.size());
        assertEquals("SRV-PAYMENT", list.get(0).serviceId());
        assertEquals(0, list.get(0).incidentCount());
    }

    @Test
    void getServiceIncidentCounts_asEmployee_throwsUnauthorized() {
        assertThrows(UnauthorizedOperationException.class, () ->
                controller.getServiceIncidentCounts(employeeAuth));
    }
}
