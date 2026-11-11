package com.iitsm.domain.sla;

import com.iitsm.domain.exception.InvalidInputException;
import java.time.Instant;

/**
 * Result record holding response and resolution deadlines.
 */
public record SlaDeadlines(Instant responseDeadline, Instant resolutionDeadline) {

    public SlaDeadlines {
        if (responseDeadline == null) {
            throw new InvalidInputException("responseDeadline must not be null");
        }
        if (resolutionDeadline == null) {
            throw new InvalidInputException("resolutionDeadline must not be null");
        }
    }
}
