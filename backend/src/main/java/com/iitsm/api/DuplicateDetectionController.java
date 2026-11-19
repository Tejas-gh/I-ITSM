package com.iitsm.api;

import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.iitsm.api.dto.CheckDuplicateRequest;
import com.iitsm.api.dto.CheckDuplicateResponse;
import com.iitsm.application.service.DuplicateDetectionService;

/**
 * REST controller providing advisory duplicate detection for incidents:
 * <ul>
 *   <li>POST /api/incidents/check-duplicates</li>
 *   <li>POST /api/check-duplicates</li>
 * </ul>
 *
 * <p>Exposes only safe fields (id, title, status, score, matchReason) without leaking
 * reporter identity or full ticket descriptions.</p>
 */
@RestController
public class DuplicateDetectionController {

    private final DuplicateDetectionService duplicateDetectionService;

    public DuplicateDetectionController(DuplicateDetectionService duplicateDetectionService) {
        this.duplicateDetectionService = Objects.requireNonNull(duplicateDetectionService, "duplicateDetectionService must not be null");
    }

    @PostMapping({"/api/incidents/check-duplicates", "/api/check-duplicates"})
    @Transactional(readOnly = true)
    public CheckDuplicateResponse checkDuplicates(@RequestBody(required = false) CheckDuplicateRequest request) {
        return duplicateDetectionService.checkDuplicates(request);
    }
}
