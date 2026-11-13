package com.iitsm.api.dto;

import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.Urgency;

/** Body of {@code PUT /api/incidents/{id}/priority-inputs}. */
public record UpdatePriorityInputsRequest(Impact impact, Urgency urgency) {
}
