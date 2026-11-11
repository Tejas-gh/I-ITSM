package com.iitsm.domain.sla;

import com.iitsm.domain.exception.InvalidInputException;
import java.time.Instant;

/**
 * Result record holding evaluation of response and resolution SLAs against a specific timestamp.
 */
public record SlaEvaluation(
        SlaStatus response,
        SlaStatus resolution,
        SlaStatus overall,
        Instant effectiveResponseDeadline,
        Instant effectiveResolutionDeadline
) {

    public SlaEvaluation {
        if (response == null) {
            throw new InvalidInputException("response status must not be null");
        }
        if (resolution == null) {
            throw new InvalidInputException("resolution status must not be null");
        }
        if (overall == null) {
            throw new InvalidInputException("overall status must not be null");
        }
        if (effectiveResponseDeadline == null) {
            throw new InvalidInputException("effectiveResponseDeadline must not be null");
        }
        if (effectiveResolutionDeadline == null) {
            throw new InvalidInputException("effectiveResolutionDeadline must not be null");
        }
    }
}
