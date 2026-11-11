package com.iitsm.application.service;

import com.iitsm.application.command.SaveItServiceCommand;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.repository.ItServiceRepository;
import com.iitsm.domain.repository.UserRepository;

import java.util.List;
import java.util.Optional;

/**
 * Administrative application service for managing IT services and their business criticalities.
 */
public final class ItServiceAdminService {

    private final ItServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final AdminPermissionPolicy permissionPolicy;

    public ItServiceAdminService(ItServiceRepository serviceRepository,
                                 UserRepository userRepository,
                                 AdminPermissionPolicy permissionPolicy) {
        if (serviceRepository == null) {
            throw new InvalidInputException("serviceRepository must not be null");
        }
        if (userRepository == null) {
            throw new InvalidInputException("userRepository must not be null");
        }
        if (permissionPolicy == null) {
            throw new InvalidInputException("permissionPolicy must not be null");
        }
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
        this.permissionPolicy = permissionPolicy;
    }

    /**
     * Creates or updates an IT service. Restricted to administrators.
     */
    public ItService saveService(SaveItServiceCommand command) {
        if (command == null) {
            throw new InvalidInputException("command must not be null");
        }
        permissionPolicy.check(command.actorId(), userRepository);

        ItService service = new ItService(
                command.id(),
                command.name(),
                command.businessCriticality()
        );
        return serviceRepository.save(service);
    }

    public Optional<ItService> findById(String id) {
        if (id == null || id.isBlank()) {
            throw new InvalidInputException("id must not be blank");
        }
        return serviceRepository.findById(id);
    }

    public List<ItService> findAll() {
        return serviceRepository.findAll();
    }
}
