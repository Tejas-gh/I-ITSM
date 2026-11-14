package com.iitsm.api;

import com.iitsm.api.dto.ItServiceResponse;
import com.iitsm.api.dto.SaveItServiceRequest;
import com.iitsm.application.command.SaveItServiceCommand;
import com.iitsm.application.service.ItServiceAdminService;
import com.iitsm.domain.exception.ServiceNotFoundException;
import com.iitsm.domain.model.ItService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for managing IT services and their business criticalities.
 * Creation and mutation are restricted to administrators.
 */
@RestController
@RequestMapping({"/api/admin/services", "/api/services"})
@Transactional
public class ItServiceAdminController {

    private final ItServiceAdminService serviceAdminService;

    public ItServiceAdminController(ItServiceAdminService serviceAdminService) {
        this.serviceAdminService = serviceAdminService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItServiceResponse saveService(@RequestBody SaveItServiceRequest request, Authentication authentication) {
        SaveItServiceCommand command = new SaveItServiceCommand(
                authentication.getName(),
                request.id(),
                request.name(),
                request.businessCriticality()
        );
        ItService saved = serviceAdminService.saveService(command);
        return ItServiceResponse.from(saved);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ItServiceResponse> listServices() {
        return serviceAdminService.findAll()
                .stream()
                .map(ItServiceResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ItServiceResponse getService(@PathVariable String id) {
        return serviceAdminService.findById(id)
                .map(ItServiceResponse::from)
                .orElseThrow(() -> new ServiceNotFoundException("Service not found: " + id));
    }
}
