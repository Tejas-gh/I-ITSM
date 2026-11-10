package com.iitsm.domain.priority;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.Urgency;

public record PriorityInput(Impact impact, Urgency urgency,
                            BusinessCriticality businessCriticality, int affectedUsers) {

    public PriorityInput {
        if (impact == null) {
            throw new InvalidInputException("impact must not be null");
        }
        if (urgency == null) {
            throw new InvalidInputException("urgency must not be null");
        }
        if (businessCriticality == null) {
            throw new InvalidInputException("businessCriticality must not be null");
        }
        if (affectedUsers < 1) {
            throw new InvalidInputException("affectedUsers must be >= 1");
        }
    }
}
