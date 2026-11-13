package com.iitsm.api.dto;

import com.iitsm.domain.model.BusinessCriticality;

/**
 * Request payload for creating or updating an IT service.
 */
public record SaveItServiceRequest(
        String id,
        String name,
        BusinessCriticality businessCriticality
) {
}
