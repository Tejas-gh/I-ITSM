package com.iitsm.api;

import com.iitsm.api.dto.CategoryResponse;
import com.iitsm.api.dto.ItServiceResponse;
import com.iitsm.api.dto.SaveCategoryRequest;
import com.iitsm.api.dto.SaveItServiceRequest;
import com.iitsm.api.dto.SaveSlaPolicyRequest;
import com.iitsm.api.dto.SlaPolicyResponse;
import com.iitsm.application.service.AdminPermissionPolicy;
import com.iitsm.application.service.CategoryAdminService;
import com.iitsm.application.service.ItServiceAdminService;
import com.iitsm.application.service.SlaPolicyAdminService;
import com.iitsm.domain.exception.CategoryNotFoundException;
import com.iitsm.domain.exception.MissingSlaException;
import com.iitsm.domain.exception.ServiceNotFoundException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.SlaPolicy;
import com.iitsm.domain.repository.CategoryRepository;
import com.iitsm.domain.repository.ItServiceRepository;
import com.iitsm.domain.repository.SlaPolicyRepository;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryCategoryRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryItServiceRepository;
import com.iitsm.infrastructure.persistence.memory.InMemorySlaPolicyRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogAdminControllersTest {

    private ItServiceRepository serviceRepo;
    private CategoryRepository categoryRepo;
    private SlaPolicyRepository slaRepo;
    private UserRepository userRepo;

    private ItServiceAdminController serviceController;
    private CategoryAdminController categoryController;
    private SlaPolicyAdminController slaController;

    private static Authentication authFor(String userId) {
        return new TestingAuthenticationToken(userId, null);
    }

    private final Authentication adminAuth = authFor("ADMIN01");
    private final Authentication employeeAuth = authFor("EMP01");

    @BeforeEach
    void setUp() {
        serviceRepo = new InMemoryItServiceRepository();
        categoryRepo = new InMemoryCategoryRepository();
        slaRepo = new InMemorySlaPolicyRepository();
        userRepo = new InMemoryUserRepository();

        Administrator admin = TestData.admin("ADMIN01");
        Employee employee = TestData.employee("EMP01");
        userRepo.save(admin);
        userRepo.save(employee);

        AdminPermissionPolicy policy = new AdminPermissionPolicy();
        ItServiceAdminService itServiceAdminService = new ItServiceAdminService(serviceRepo, userRepo, policy);
        CategoryAdminService categoryAdminService = new CategoryAdminService(categoryRepo, userRepo, policy);
        SlaPolicyAdminService slaPolicyAdminService = new SlaPolicyAdminService(slaRepo, serviceRepo, userRepo, policy);

        serviceController = new ItServiceAdminController(itServiceAdminService);
        categoryController = new CategoryAdminController(categoryAdminService);
        slaController = new SlaPolicyAdminController(slaPolicyAdminService);
    }

    // ItServiceAdminController tests
    @Test
    void saveService_asAdmin_createsService() {
        SaveItServiceRequest request = new SaveItServiceRequest("SVC_PAY", "Payments", BusinessCriticality.CRITICAL);
        ItServiceResponse response = serviceController.saveService(request, adminAuth);

        assertEquals("SVC_PAY", response.id());
        assertEquals("Payments", response.name());
        assertEquals(BusinessCriticality.CRITICAL, response.businessCriticality());
    }

    @Test
    void saveService_asEmployee_throwsUnauthorized() {
        SaveItServiceRequest request = new SaveItServiceRequest("SVC_PAY", "Payments", BusinessCriticality.CRITICAL);
        assertThrows(UnauthorizedOperationException.class, () ->
                serviceController.saveService(request, employeeAuth));
    }

    @Test
    void getService_existing_returnsService() {
        serviceRepo.save(new ItService("SVC1", "Email", BusinessCriticality.HIGH));
        ItServiceResponse response = serviceController.getService("SVC1");

        assertEquals("SVC1", response.id());
        assertEquals("Email", response.name());
    }

    @Test
    void getService_notFound_throwsException() {
        assertThrows(ServiceNotFoundException.class, () ->
                serviceController.getService("NONEXISTENT"));
    }

    @Test
    void listServices_returnsAllServices() {
        serviceRepo.save(new ItService("SVC1", "Email", BusinessCriticality.HIGH));
        serviceRepo.save(new ItService("SVC2", "VPN", BusinessCriticality.CRITICAL));

        List<ItServiceResponse> list = serviceController.listServices();
        assertEquals(2, list.size());
    }

    // CategoryAdminController tests
    @Test
    void saveCategory_asAdmin_createsCategory() {
        SaveCategoryRequest request = new SaveCategoryRequest("CAT_NET", "Network", true);
        CategoryResponse response = categoryController.saveCategory(request, adminAuth);

        assertEquals("CAT_NET", response.id());
        assertEquals("Network", response.name());
        assertTrue(response.active());
    }

    @Test
    void saveCategory_asEmployee_throwsUnauthorized() {
        SaveCategoryRequest request = new SaveCategoryRequest("CAT_NET", "Network", true);
        assertThrows(UnauthorizedOperationException.class, () ->
                categoryController.saveCategory(request, employeeAuth));
    }

    @Test
    void activateAndDeactivateCategory_asAdmin_updatesActiveStatus() {
        categoryRepo.save(new Category("CAT1", "Software"));

        CategoryResponse deactivated = categoryController.deactivateCategory("CAT1", adminAuth);
        assertFalse(deactivated.active());

        CategoryResponse activated = categoryController.activateCategory("CAT1", adminAuth);
        assertTrue(activated.active());
    }

    @Test
    void activateCategory_asEmployee_throwsUnauthorized() {
        categoryRepo.save(new Category("CAT1", "Software"));
        assertThrows(UnauthorizedOperationException.class, () ->
                categoryController.activateCategory("CAT1", employeeAuth));
    }

    @Test
    void getCategory_notFound_throwsException() {
        assertThrows(CategoryNotFoundException.class, () ->
                categoryController.getCategory("UNKNOWN"));
    }

    @Test
    void listCategories_and_listActiveCategories() {
        Category active = new Category("C1", "Cat 1");
        Category inactive = new Category("C2", "Cat 2");
        inactive.deactivate();
        categoryRepo.save(active);
        categoryRepo.save(inactive);

        assertEquals(2, categoryController.listCategories().size());
        assertEquals(1, categoryController.listActiveCategories().size());
    }

    // SlaPolicyAdminController tests
    @Test
    void savePolicy_asAdmin_createsPolicy() {
        SaveSlaPolicyRequest request = new SaveSlaPolicyRequest(
                null,
                PriorityLevel.P1,
                Duration.ofMinutes(5),
                Duration.ofMinutes(30)
        );
        SlaPolicyResponse response = slaController.savePolicy(request, adminAuth);

        assertNotNull(response);
        assertEquals(PriorityLevel.P1, response.priority());
        assertEquals(Duration.ofMinutes(5), response.responseTarget());
        assertEquals(Duration.ofMinutes(30), response.resolutionTarget());
    }

    @Test
    void savePolicy_asEmployee_throwsUnauthorized() {
        SaveSlaPolicyRequest request = new SaveSlaPolicyRequest(
                null,
                PriorityLevel.P1,
                Duration.ofMinutes(5),
                Duration.ofMinutes(30)
        );
        assertThrows(UnauthorizedOperationException.class, () ->
                slaController.savePolicy(request, employeeAuth));
    }

    @Test
    void getDefaultPolicy_existing_returnsPolicy() {
        slaRepo.save(new SlaPolicy(null, PriorityLevel.P2, Duration.ofMinutes(15), Duration.ofMinutes(120)));
        SlaPolicyResponse response = slaController.getDefaultPolicy(PriorityLevel.P2);

        assertEquals(PriorityLevel.P2, response.priority());
        assertEquals(Duration.ofMinutes(15), response.responseTarget());
    }

    @Test
    void getDefaultPolicy_missing_throwsException() {
        assertThrows(MissingSlaException.class, () ->
                slaController.getDefaultPolicy(PriorityLevel.P1));
    }

    @Test
    void getOverridePolicy_existing_returnsPolicy() {
        serviceRepo.save(new ItService("SVC1", "Payment", BusinessCriticality.CRITICAL));
        slaRepo.save(new SlaPolicy("SVC1", PriorityLevel.P1, Duration.ofMinutes(2), Duration.ofMinutes(15)));

        SlaPolicyResponse response = slaController.getOverridePolicy("SVC1", PriorityLevel.P1);

        assertEquals("SVC1", response.serviceId());
        assertEquals(PriorityLevel.P1, response.priority());
        assertEquals(Duration.ofMinutes(2), response.responseTarget());
    }

    @Test
    void getOverridePolicy_missing_throwsException() {
        assertThrows(MissingSlaException.class, () ->
                slaController.getOverridePolicy("SVC1", PriorityLevel.P1));
    }

    @Test
    void listPolicies_returnsAll() {
        slaRepo.save(new SlaPolicy(null, PriorityLevel.P1, Duration.ofMinutes(5), Duration.ofMinutes(30)));
        slaRepo.save(new SlaPolicy(null, PriorityLevel.P2, Duration.ofMinutes(15), Duration.ofMinutes(120)));

        assertEquals(2, slaController.listPolicies().size());
    }
}
