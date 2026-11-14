package com.iitsm.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iitsm.api.dto.StrategyResponse;
import com.iitsm.api.dto.StrategySelectionRequest;
import com.iitsm.application.service.AssignmentStrategyAdminService;

/**
 * Administrator selection of the technician-assignment strategy. The selection is held in memory, so no database
 * transaction is involved. The administrator check is made by the application service.
 */
@RestController
@RequestMapping("/api/admin/assignment-strategy")
public class AssignmentStrategyController {

    private final AssignmentStrategyAdminService strategies;

    public AssignmentStrategyController(AssignmentStrategyAdminService strategies) {
        this.strategies = strategies;
    }

    @GetMapping
    public StrategyResponse get(Authentication authentication) {
        return StrategyResponse.from(strategies.getSelection(authentication.getName()));
    }

    @PutMapping
    public StrategyResponse select(@RequestBody StrategySelectionRequest request, Authentication authentication) {
        return StrategyResponse.from(strategies.select(authentication.getName(), request.strategy()));
    }
}
