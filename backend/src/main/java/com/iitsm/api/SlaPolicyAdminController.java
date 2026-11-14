package com.iitsm.api;

import com.iitsm.api.dto.SaveSlaPolicyRequest;
import com.iitsm.api.dto.SlaPolicyResponse;
import com.iitsm.application.command.SaveSlaPolicyCommand;
import com.iitsm.application.service.SlaPolicyAdminService;
import com.iitsm.domain.exception.MissingSlaException;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.SlaPolicy;
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
 * REST controller for configuring default and per-service override SLA policies.
 * Creation and mutation are restricted to administrators.
 */
@RestController
@RequestMapping("/api/admin/sla-policies")
@Transactional
public class SlaPolicyAdminController {

    private final SlaPolicyAdminService slaPolicyAdminService;

    public SlaPolicyAdminController(SlaPolicyAdminService slaPolicyAdminService) {
        this.slaPolicyAdminService = slaPolicyAdminService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SlaPolicyResponse savePolicy(@RequestBody SaveSlaPolicyRequest request, Authentication authentication) {
        SaveSlaPolicyCommand command = new SaveSlaPolicyCommand(
                authentication.getName(),
                request.serviceId(),
                request.priority(),
                request.responseTarget(),
                request.resolutionTarget()
        );
        SlaPolicy saved = slaPolicyAdminService.savePolicy(command);
        return SlaPolicyResponse.from(saved);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<SlaPolicyResponse> listPolicies() {
        return slaPolicyAdminService.findAll()
                .stream()
                .map(SlaPolicyResponse::from)
                .toList();
    }

    @GetMapping("/default/{priority}")
    @Transactional(readOnly = true)
    public SlaPolicyResponse getDefaultPolicy(@PathVariable PriorityLevel priority) {
        return slaPolicyAdminService.findDefault(priority)
                .map(SlaPolicyResponse::from)
                .orElseThrow(() -> new MissingSlaException("No default SLA policy found for priority: " + priority));
    }

    @GetMapping("/override/{serviceId}/{priority}")
    @Transactional(readOnly = true)
    public SlaPolicyResponse getOverridePolicy(@PathVariable String serviceId,
                                               @PathVariable PriorityLevel priority) {
        return slaPolicyAdminService.findOverride(serviceId, priority)
                .map(SlaPolicyResponse::from)
                .orElseThrow(() -> new MissingSlaException(
                        "No override SLA policy found for service " + serviceId + " and priority: " + priority));
    }
}
