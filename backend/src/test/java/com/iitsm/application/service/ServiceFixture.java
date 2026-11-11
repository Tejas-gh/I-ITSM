package com.iitsm.application.service;

import com.iitsm.domain.classification.CategoryClassifier;
import com.iitsm.domain.classification.NoOpClassifier;
import com.iitsm.domain.factory.IncidentFactory;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.model.SlaPolicy;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.priority.PriorityStrategy;
import com.iitsm.domain.priority.WeightedPriorityStrategy;
import com.iitsm.domain.repository.CategoryRepository;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.repository.ItServiceRepository;
import com.iitsm.domain.repository.SlaPolicyRepository;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.domain.sla.SlaCalculator;
import com.iitsm.domain.sla.SlaPolicyResolver;
import com.iitsm.infrastructure.event.InMemoryDomainEventPublisher;
import com.iitsm.infrastructure.persistence.memory.InMemoryCategoryRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryIncidentIdGenerator;
import com.iitsm.infrastructure.persistence.memory.InMemoryIncidentRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryItServiceRepository;
import com.iitsm.infrastructure.persistence.memory.InMemorySlaPolicyRepository;
import com.iitsm.infrastructure.persistence.memory.InMemoryUserRepository;
import com.iitsm.support.TestClock;
import com.iitsm.support.TestData;

public final class ServiceFixture {

    public final TestClock clock = new TestClock();
    public final IncidentRepository incidents = new InMemoryIncidentRepository();
    public final UserRepository users = new InMemoryUserRepository();
    public final ItServiceRepository services = new InMemoryItServiceRepository();
    public final CategoryRepository categories = new InMemoryCategoryRepository();
    public final SlaPolicyRepository slaPolicies = new InMemorySlaPolicyRepository();

    public final InMemoryIncidentIdGenerator idGenerator = new InMemoryIncidentIdGenerator();
    public final PriorityStrategy priorityStrategy = new WeightedPriorityStrategy();
    public final IncidentFactory incidentFactory = new IncidentFactory(idGenerator, priorityStrategy);

    public final SlaPolicyResolver slaResolver = new SlaPolicyResolver(slaPolicies);
    public final SlaCalculator slaCalculator = new SlaCalculator(slaResolver);
    public final StubAssignmentStrategy assignmentStrategy = new StubAssignmentStrategy();
    public final CategoryClassifier classifier = new NoOpClassifier();
    public final IncidentPermissionPolicy permissions = new IncidentPermissionPolicy();
    public final InMemoryDomainEventPublisher events = new InMemoryDomainEventPublisher();
    public final RecordingListener listener = new RecordingListener();

    public final IncidentService service;

    public final Employee employee = TestData.employee("EMP01");
    public final Technician technician = TestData.technician("TECH01", 5);
    public final Technician technician2 = TestData.technician("TECH02", 5);
    public final Administrator admin = TestData.admin("ADMIN01");
    public final ItService itService = TestData.service("SRV-PAYMENT", BusinessCriticality.CRITICAL);
    public final Category category = TestData.category("APPLICATION");

    public ServiceFixture() {
        events.subscribe(listener);

        // Seed reference data
        users.save(employee);
        users.save(technician);
        users.save(technician2);
        users.save(admin);

        services.save(itService);
        for (Category cat : TestData.defaultCategories()) {
            categories.save(cat);
        }
        for (SlaPolicy policy : TestData.defaultSlaPolicies()) {
            slaPolicies.save(policy);
        }

        assignmentStrategy.setDesignatedTechnician(technician);

        service = new IncidentService(
                incidents,
                users,
                services,
                categories,
                incidentFactory,
                priorityStrategy,
                slaCalculator,
                assignmentStrategy,
                classifier,
                permissions,
                events,
                clock
        );
    }
}
