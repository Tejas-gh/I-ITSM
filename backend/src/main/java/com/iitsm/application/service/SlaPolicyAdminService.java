package com.iitsm.application.service;

import com.iitsm.application.command.SaveSlaPolicyCommand;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.ServiceNotFoundException;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.SlaPolicy;
import com.iitsm.domain.repository.ItServiceRepository;
import com.iitsm.domain.repository.SlaPolicyRepository;
import com.iitsm.domain.repository.UserRepository;

import java.util.List;
import java.util.Optional;

/**
 * Administrative application service for configuring default and per-service override SLA policies.
 */
public final class SlaPolicyAdminService {

    private final SlaPolicyRepository policyRepository;
    private final ItServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final AdminPermissionPolicy permissionPolicy;

    public SlaPolicyAdminService(SlaPolicyRepository policyRepository,
                                 ItServiceRepository serviceRepository,
                                 UserRepository userRepository,
                                 AdminPermissionPolicy permissionPolicy) {
        if (policyRepository == null) {
            throw new InvalidInputException("policyRepository must not be null");
        }
        if (serviceRepository == null) {
            throw new InvalidInputException("serviceRepository must not be null");
        }
        if (userRepository == null) {
            throw new InvalidInputException("userRepository must not be null");
        }
        if (permissionPolicy == null) {
            throw new InvalidInputException("permissionPolicy must not be null");
        }
        this.policyRepository = policyRepository;
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
        this.permissionPolicy = permissionPolicy;
    }

    /**
     * Saves a default or per-service override SLA policy. Restricted to administrators.
     */
    public SlaPolicy savePolicy(SaveSlaPolicyCommand command) {
        if (command == null) {
            throw new InvalidInputException("command must not be null");
        }
        permissionPolicy.check(command.actorId(), userRepository);

        if (command.serviceId() != null && !command.serviceId().isBlank()) {
            serviceRepository.findById(command.serviceId())
                    .orElseThrow(() -> new ServiceNotFoundException("Service not found: " + command.serviceId()));
        }

        SlaPolicy policy = new SlaPolicy(
                command.serviceId(),
                command.priority(),
                command.responseTarget(),
                command.resolutionTarget()
        );
        return policyRepository.save(policy);
    }

    public List<SlaPolicy> findAll() {
        return policyRepository.findAll();
    }

    public Optional<SlaPolicy> findOverride(String serviceId, PriorityLevel priority) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new InvalidInputException("serviceId must not be blank");
        }
        if (priority == null) {
            throw new InvalidInputException("priority must not be null");
        }
        return policyRepository.findOverride(serviceId, priority);
    }

    public Optional<SlaPolicy> findDefault(PriorityLevel priority) {
        if (priority == null) {
            throw new InvalidInputException("priority must not be null");
        }
        return policyRepository.findDefault(priority);
    }
}
