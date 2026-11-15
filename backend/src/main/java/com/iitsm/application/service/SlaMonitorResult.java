package com.iitsm.application.service;

/**
 * Summary metrics of an SLA monitor execution tick.
 */
public record SlaMonitorResult(
        int scannedCount,
        int warningsRaised,
        int violationsRaised,
        int errorCount
) {
}
