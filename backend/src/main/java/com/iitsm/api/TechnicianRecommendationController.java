package com.iitsm.api;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iitsm.api.dto.TechnicianRecommendationResponse;
import com.iitsm.application.service.TechnicianRecommendationService;

/**
 * Controller exposing smart technician recommendations for incidents.
 */
@RestController
@RequestMapping("/api/incidents")
public class TechnicianRecommendationController {

    private final TechnicianRecommendationService recommendationService;

    public TechnicianRecommendationController(TechnicianRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/{id}/recommendations")
    @Transactional(readOnly = true)
    public List<TechnicianRecommendationResponse> getRecommendations(@PathVariable("id") String id,
                                                                     Authentication authentication) {
        String actorId = authentication.getName();
        return recommendationService.getRecommendations(actorId, id).stream()
                .map(TechnicianRecommendationResponse::from)
                .toList();
    }
}
