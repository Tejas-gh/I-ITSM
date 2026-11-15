package com.iitsm.domain.sla;

/**
 * The classification of an SLA alert:
 * - {@link #WARNING}: Triggered when an incident nears its response or resolution deadline (e.g. ≤ 20% remaining).
 * - {@link #VIOLATION}: Triggered when an incident breaches its response or resolution deadline.
 */
public enum SlaAlertKind {
    WARNING,
    VIOLATION
}
