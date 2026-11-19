package com.iitsm.application.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.iitsm.api.dto.CheckDuplicateRequest;
import com.iitsm.api.dto.CheckDuplicateResponse;
import com.iitsm.api.dto.DuplicateCandidateResponse;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.repository.IncidentRepository;

/**
 * Service for advisory duplicate detection of incidents.
 *
 * <p>Scans open incidents for similar titles and matching service/category contexts
 * using keyword token overlap and Jaccard similarity metrics. Exposes ONLY safe
 * non-sensitive visibility fields to the reporter.</p>
 */
public class DuplicateDetectionService {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "in", "on", "at", "to", "for", "with",
            "is", "it", "of", "my", "our", "this", "that", "not", "no", "we", "be",
            "do", "as", "by", "from", "has", "have", "are", "was", "were", "but"
    );

    private final IncidentRepository incidentRepository;

    public DuplicateDetectionService(IncidentRepository incidentRepository) {
        this.incidentRepository = Objects.requireNonNull(incidentRepository, "incidentRepository must not be null");
    }

    /**
     * Checks for potential open duplicates matching the given request.
     *
     * @param request the duplicate check inputs
     * @return advisory response with candidate matches (safe fields only)
     */
    public CheckDuplicateResponse checkDuplicates(CheckDuplicateRequest request) {
        if (request == null || request.title() == null || request.title().trim().isEmpty()) {
            return new CheckDuplicateResponse(false, Collections.emptyList());
        }

        String queryTitle = request.title().trim();
        Set<String> queryTokens = extractTokens(queryTitle);
        if (queryTokens.isEmpty()) {
            return new CheckDuplicateResponse(false, Collections.emptyList());
        }

        List<Incident> allIncidents = incidentRepository.findAll();
        List<DuplicateCandidateResponse> candidates = new ArrayList<>();

        for (Incident inc : allIncidents) {
            // Ignore closed incidents
            if (inc == null || inc.getStatus() == IncidentStatus.CLOSED) {
                continue;
            }

            String targetTitle = inc.getTitle();
            if (targetTitle == null || targetTitle.trim().isEmpty()) {
                continue;
            }

            // 1. Exact match check (case-insensitive)
            if (queryTitle.equalsIgnoreCase(targetTitle.trim())) {
                candidates.add(new DuplicateCandidateResponse(
                        inc.getId(),
                        inc.getTitle(),
                        inc.getStatus(),
                        1.0,
                        "Exact title match with active open incident"
                ));
                continue;
            }

            // 2. Token overlap & similarity scoring
            Set<String> targetTokens = extractTokens(targetTitle);
            if (targetTokens.isEmpty()) {
                continue;
            }

            Set<String> intersection = new HashSet<>(queryTokens);
            intersection.retainAll(targetTokens);

            if (intersection.isEmpty()) {
                continue;
            }

            Set<String> union = new HashSet<>(queryTokens);
            union.addAll(targetTokens);

            double jaccard = (double) intersection.size() / union.size();
            double overlapRatio = (double) intersection.size() / Math.min(queryTokens.size(), targetTokens.size());
            double baseScore = (0.5 * jaccard) + (0.5 * overlapRatio);

            // Context bonuses
            boolean sameService = request.serviceId() != null
                    && !request.serviceId().isBlank()
                    && inc.getService() != null
                    && request.serviceId().equalsIgnoreCase(inc.getService().getId());
            boolean sameCategory = request.categoryId() != null
                    && !request.categoryId().isBlank()
                    && inc.getCategory() != null
                    && request.categoryId().equalsIgnoreCase(inc.getCategory().getId());

            double bonus = (sameService ? 0.20 : 0.0) + (sameCategory ? 0.15 : 0.0);
            double totalScore = Math.min(1.0, baseScore + bonus);

            // Qualification threshold: score >= 0.35, or at least 2 common tokens
            if (totalScore >= 0.35 || intersection.size() >= 2) {
                List<String> sortedTokens = new ArrayList<>(intersection);
                Collections.sort(sortedTokens);

                StringBuilder reason = new StringBuilder();
                reason.append("Shares keyword(s): [").append(String.join(", ", sortedTokens)).append("]");
                if (sameService && sameCategory) {
                    reason.append(" within same service and category");
                } else if (sameService) {
                    reason.append(" within same service");
                } else if (sameCategory) {
                    reason.append(" within same category");
                }

                candidates.add(new DuplicateCandidateResponse(
                        inc.getId(),
                        inc.getTitle(),
                        inc.getStatus(),
                        Math.round(totalScore * 100.0) / 100.0,
                        reason.toString()
                ));
            }
        }

        // Sort by score descending, limit to top 5
        candidates.sort(Comparator.comparingDouble(DuplicateCandidateResponse::score).reversed());
        if (candidates.size() > 5) {
            candidates = candidates.subList(0, 5);
        }

        return new CheckDuplicateResponse(!candidates.isEmpty(), candidates);
    }

    private Set<String> extractTokens(String text) {
        if (text == null) {
            return Collections.emptySet();
        }
        return Arrays.stream(text.toLowerCase().split("[^a-zA-Z0-9]+"))
                .map(String::trim)
                .filter(w -> w.length() >= 3)
                .filter(w -> !STOP_WORDS.contains(w))
                .collect(Collectors.toSet());
    }
}
