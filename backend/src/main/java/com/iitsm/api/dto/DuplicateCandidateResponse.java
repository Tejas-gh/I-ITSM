package com.iitsm.api.dto;

import com.iitsm.domain.model.IncidentStatus;

/**
 * Advisory candidate representation for an existing open incident.
 *
 * <p>Strict visibility rule (B7): Only safe fields (id, title, status, advisory score/reason)
 * are exposed. Sensitive reporter information and full descriptions are strictly omitted.</p>
 */
public record DuplicateCandidateResponse(
        String id,
        String title,
        IncidentStatus status,
        double score,
        String matchReason
) {
}
