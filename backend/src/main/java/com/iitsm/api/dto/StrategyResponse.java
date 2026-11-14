package com.iitsm.api.dto;

import java.util.List;

import com.iitsm.application.service.AssignmentStrategyAdminService;

/** The active assignment strategy and the strategies that can be selected. */
public record StrategyResponse(String selected, List<String> available) {

    public static StrategyResponse from(AssignmentStrategyAdminService.Selection selection) {
        return new StrategyResponse(selection.selected(), selection.available());
    }
}
