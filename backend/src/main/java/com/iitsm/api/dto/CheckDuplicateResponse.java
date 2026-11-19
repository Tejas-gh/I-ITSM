package com.iitsm.api.dto;

import java.util.Collections;
import java.util.List;

/**
 * Advisory response returned from duplicate checking.
 */
public record CheckDuplicateResponse(
        boolean hasDuplicates,
        List<DuplicateCandidateResponse> candidates
) {
    public CheckDuplicateResponse {
        candidates = candidates != null ? List.copyOf(candidates) : Collections.emptyList();
    }
}
