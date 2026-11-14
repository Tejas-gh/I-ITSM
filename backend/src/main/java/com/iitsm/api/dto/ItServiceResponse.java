package com.iitsm.api.dto;

import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.ItService;

/**
 * Representation of an IT service returned over HTTP.
 */
public record ItServiceResponse(
        String id,
        String name,
        BusinessCriticality businessCriticality
) {
    public static ItServiceResponse from(ItService s) {
        return new ItServiceResponse(s.getId(), s.getName(), s.getBusinessCriticality());
    }
}
