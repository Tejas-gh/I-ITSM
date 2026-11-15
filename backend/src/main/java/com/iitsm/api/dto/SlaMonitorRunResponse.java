package com.iitsm.api.dto;

import java.time.Instant;

import com.iitsm.application.service.SlaMonitorResult;

/**
 * REST response DTO for a manual SLA monitor execution run.
 */
public record SlaMonitorRunResponse(
        int scannedCount,
        int warningsRaised,
        int violationsRaised,
        int errorCount,
        Instant ranAt
) {
    public static SlaMonitorRunResponse from(SlaMonitorResult res, Instant ranAt) {
        return new SlaMonitorRunResponse(
                res.scannedCount(),
                res.warningsRaised(),
                res.violationsRaised(),
                res.errorCount(),
                ranAt
        );
    }
}
