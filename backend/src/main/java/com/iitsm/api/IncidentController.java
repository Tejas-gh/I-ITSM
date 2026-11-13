package com.iitsm.api;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.iitsm.api.dto.AssignIncidentRequest;
import com.iitsm.api.dto.CategorySuggestionResponse;
import com.iitsm.api.dto.CloseIncidentRequest;
import com.iitsm.api.dto.CreateIncidentRequest;
import com.iitsm.api.dto.IncidentResponse;
import com.iitsm.api.dto.IncidentResponseMapper;
import com.iitsm.api.dto.IncidentTextRequest;
import com.iitsm.api.dto.SubmitFeedbackRequest;
import com.iitsm.api.dto.SuggestCategoryRequest;
import com.iitsm.api.dto.UpdatePriorityInputsRequest;
import com.iitsm.application.command.AssignIncidentCommand;
import com.iitsm.application.command.CloseIncidentCommand;
import com.iitsm.application.command.CreateIncidentCommand;
import com.iitsm.application.command.IncidentTextCommand;
import com.iitsm.application.command.SubmitFeedbackCommand;
import com.iitsm.application.command.UpdatePriorityInputsCommand;
import com.iitsm.application.service.IncidentSearchQuery;
import com.iitsm.application.service.IncidentSearchService;
import com.iitsm.application.service.IncidentService;
import com.iitsm.domain.classification.CategorySuggestion;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.sla.SlaStatus;

/**
 * Incident REST API (CONTRACT_FREEZE.md §U.1). Translates HTTP to application commands and back; contains no
 * business rules. The actor id is always the authenticated user (JWT principal name), never taken from a body.
 * Transactions are demarcated here; the application services carry no annotations.
 */
@RestController
@RequestMapping("/api/incidents")
@Transactional
public class IncidentController {

    private final IncidentService incidents;
    private final IncidentSearchService search;

    public IncidentController(IncidentService incidents, IncidentSearchService search) {
        this.incidents = incidents;
        this.search = search;
    }

    // ---------- create / read ----------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentResponse create(@RequestBody CreateIncidentRequest request, Authentication authentication) {
        return IncidentResponseMapper.toResponse(incidents.createIncident(new CreateIncidentCommand(
                actor(authentication), request.title(), request.description(), request.serviceId(),
                request.categoryId(), request.suggestedCategoryId(), request.categorySource(),
                request.impact(), request.urgency(), request.affectedUsers())));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<IncidentResponse> list(
            @RequestParam(name = "incidentId", required = false) String incidentId,
            @RequestParam(name = "createdFrom", required = false) String createdFrom,
            @RequestParam(name = "createdTo", required = false) String createdTo,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "priority", required = false) String priority,
            @RequestParam(name = "categoryId", required = false) String categoryId,
            @RequestParam(name = "serviceId", required = false) String serviceId,
            @RequestParam(name = "technicianId", required = false) String technicianId,
            @RequestParam(name = "slaStatus", required = false) String slaStatus,
            Authentication authentication) {
        IncidentSearchQuery query = new IncidentSearchQuery(
                incidentId,
                parseInstant("createdFrom", createdFrom),
                parseInstant("createdTo", createdTo),
                parseEnum("status", status, IncidentStatus.class),
                parseEnum("priority", priority, PriorityLevel.class),
                categoryId,
                serviceId,
                technicianId,
                parseEnum("slaStatus", slaStatus, SlaStatus.class));
        return IncidentResponseMapper.toResponses(search.search(actor(authentication), query));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public IncidentResponse get(@PathVariable("id") String id, Authentication authentication) {
        return IncidentResponseMapper.toResponse(incidents.getIncident(actor(authentication), id));
    }

    /** No suggestion available: 200 with an empty body. */
    @PostMapping("/suggest-category")
    @Transactional(readOnly = true)
    public ResponseEntity<CategorySuggestionResponse> suggestCategory(@RequestBody SuggestCategoryRequest request,
                                                                      Authentication authentication) {
        Optional<CategorySuggestion> suggestion =
                incidents.suggestCategory(actor(authentication), request.title(), request.description());
        return ResponseEntity.ok(suggestion.map(IncidentResponseMapper::toResponse).orElse(null));
    }

    // ---------- assignment (admin) ----------

    @PostMapping("/{id}/assign")
    public IncidentResponse assign(@PathVariable("id") String id, @RequestBody AssignIncidentRequest request,
                                   Authentication authentication) {
        return IncidentResponseMapper.toResponse(incidents.assignIncident(
                new AssignIncidentCommand(actor(authentication), id, request.technicianId())));
    }

    @PostMapping("/{id}/reassign")
    public IncidentResponse reassign(@PathVariable("id") String id, @RequestBody AssignIncidentRequest request,
                                     Authentication authentication) {
        return IncidentResponseMapper.toResponse(incidents.reassignIncident(
                new AssignIncidentCommand(actor(authentication), id, request.technicianId())));
    }

    @PostMapping("/{id}/auto-assign")
    public IncidentResponse autoAssign(@PathVariable("id") String id, Authentication authentication) {
        return IncidentResponseMapper.toResponse(incidents.autoAssignIncident(actor(authentication), id));
    }

    // ---------- technician ----------

    @PostMapping("/{id}/acknowledge")
    public IncidentResponse acknowledge(@PathVariable("id") String id, Authentication authentication) {
        return IncidentResponseMapper.toResponse(incidents.acknowledgeIncident(actor(authentication), id));
    }

    @PostMapping("/{id}/start-work")
    public IncidentResponse startWork(@PathVariable("id") String id, Authentication authentication) {
        return IncidentResponseMapper.toResponse(incidents.startWork(actor(authentication), id));
    }

    @PostMapping("/{id}/notes")
    public IncidentResponse addNote(@PathVariable("id") String id, @RequestBody IncidentTextRequest request,
                                    Authentication authentication) {
        return IncidentResponseMapper.toResponse(incidents.addInvestigationNote(text(authentication, id, request)));
    }

    @PostMapping("/{id}/request-info")
    public IncidentResponse requestInfo(@PathVariable("id") String id, @RequestBody IncidentTextRequest request,
                                        Authentication authentication) {
        return IncidentResponseMapper.toResponse(incidents.requestInfo(text(authentication, id, request)));
    }

    @PostMapping("/{id}/resolve")
    public IncidentResponse resolve(@PathVariable("id") String id, @RequestBody IncidentTextRequest request,
                                    Authentication authentication) {
        return IncidentResponseMapper.toResponse(incidents.resolveIncident(text(authentication, id, request)));
    }

    // ---------- reporter ----------

    @PostMapping("/{id}/respond-info")
    public IncidentResponse respondInfo(@PathVariable("id") String id, @RequestBody IncidentTextRequest request,
                                        Authentication authentication) {
        return IncidentResponseMapper.toResponse(incidents.respondToInfoRequest(text(authentication, id, request)));
    }

    @PostMapping("/{id}/additional-info")
    public IncidentResponse additionalInfo(@PathVariable("id") String id, @RequestBody IncidentTextRequest request,
                                           Authentication authentication) {
        return IncidentResponseMapper.toResponse(incidents.addAdditionalInfo(text(authentication, id, request)));
    }

    @PostMapping("/{id}/verify")
    public IncidentResponse verify(@PathVariable("id") String id, Authentication authentication) {
        return IncidentResponseMapper.toResponse(incidents.verifyResolution(actor(authentication), id));
    }

    @PostMapping("/{id}/reject-resolution")
    public IncidentResponse rejectResolution(@PathVariable("id") String id, Authentication authentication) {
        return IncidentResponseMapper.toResponse(incidents.rejectResolution(actor(authentication), id));
    }

    @PostMapping("/{id}/feedback")
    public IncidentResponse feedback(@PathVariable("id") String id, @RequestBody SubmitFeedbackRequest request,
                                     Authentication authentication) {
        return IncidentResponseMapper.toResponse(incidents.submitFeedback(
                new SubmitFeedbackCommand(actor(authentication), id, request.rating(), request.comment())));
    }

    // ---------- admin ----------

    @PostMapping("/{id}/close")
    public IncidentResponse close(@PathVariable("id") String id, @RequestBody CloseIncidentRequest request,
                                  Authentication authentication) {
        return IncidentResponseMapper.toResponse(incidents.closeIncident(
                new CloseIncidentCommand(actor(authentication), id, request.closureNote())));
    }

    // ---------- admin or assigned technician ----------

    @PutMapping("/{id}/priority-inputs")
    public IncidentResponse updatePriorityInputs(@PathVariable("id") String id,
                                                 @RequestBody UpdatePriorityInputsRequest request,
                                                 Authentication authentication) {
        return IncidentResponseMapper.toResponse(incidents.updatePriorityInputs(
                new UpdatePriorityInputsCommand(actor(authentication), id, request.impact(), request.urgency())));
    }

    // ---------- helpers ----------

    private static String actor(Authentication authentication) {
        return authentication.getName();
    }

    private static IncidentTextCommand text(Authentication authentication, String id, IncidentTextRequest request) {
        return new IncidentTextCommand(actor(authentication), id, request.text());
    }

    private static Instant parseInstant(String name, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidInputException(name + " must be an ISO-8601 instant, e.g. 2026-11-16T09:00:00Z");
        }
    }

    private static <E extends Enum<E>> E parseEnum(String name, String value, Class<E> type) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException(name + " is not a valid value: " + value);
        }
    }
}
