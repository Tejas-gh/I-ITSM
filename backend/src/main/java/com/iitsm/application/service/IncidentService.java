package com.iitsm.application.service;

import com.iitsm.application.command.AssignIncidentCommand;
import com.iitsm.application.command.CloseIncidentCommand;
import com.iitsm.application.command.CreateIncidentCommand;
import com.iitsm.application.command.IncidentTextCommand;
import com.iitsm.application.command.SubmitFeedbackCommand;
import com.iitsm.application.command.UpdatePriorityInputsCommand;
import com.iitsm.domain.assignment.AssignmentStrategy;
import com.iitsm.domain.classification.CategoryClassifier;
import com.iitsm.domain.classification.CategorySuggestion;
import com.iitsm.domain.event.AssignmentFailed;
import com.iitsm.domain.event.DomainEventPublisher;
import com.iitsm.domain.event.IncidentAcknowledged;
import com.iitsm.domain.event.IncidentAssigned;
import com.iitsm.domain.event.IncidentClassified;
import com.iitsm.domain.event.IncidentClosed;
import com.iitsm.domain.event.IncidentCreated;
import com.iitsm.domain.event.IncidentResolved;
import com.iitsm.domain.event.IncidentStatusChanged;
import com.iitsm.domain.event.IncidentUpdated;
import com.iitsm.domain.event.PriorityChanged;
import com.iitsm.domain.exception.CategoryNotFoundException;
import com.iitsm.domain.exception.IncidentNotFoundException;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.ServiceNotFoundException;
import com.iitsm.domain.exception.TechnicianOverloadedException;
import com.iitsm.domain.exception.TechnicianUnavailableException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.exception.UserNotFoundException;
import com.iitsm.domain.factory.IncidentFactory;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentDraft;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import com.iitsm.domain.priority.PriorityInput;
import com.iitsm.domain.priority.PriorityResult;
import com.iitsm.domain.priority.PriorityStrategy;
import com.iitsm.domain.repository.CategoryRepository;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.repository.ItServiceRepository;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.domain.sla.SlaCalculator;
import com.iitsm.domain.sla.SlaDeadlines;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Main application service orchestrating incident operations, permissions, persistence, and events.
 */
public final class IncidentService {

        public static final String SYSTEM_ACTOR_ID = "SYSTEM";

    /** Note text recorded when a resolution is rejected; the frontend labels notes that start with it. */
    public static final String REJECTION_NOTE = "RESOLUTION_REJECTED: the reporter rejected the proposed resolution and the incident was reopened.";

    private final IncidentRepository incidents;
    private final UserRepository users;
    private final ItServiceRepository services;
    private final CategoryRepository categories;
    private final IncidentFactory incidentFactory;
    private final PriorityStrategy priorityStrategy;
    private final SlaCalculator slaCalculator;
    private final AssignmentStrategy assignmentStrategy;
    private final CategoryClassifier classifier;
    private final IncidentPermissionPolicy permissions;
    private final DomainEventPublisher events;
    private final Clock clock;

    public IncidentService(
            IncidentRepository incidents,
            UserRepository users,
            ItServiceRepository services,
            CategoryRepository categories,
            IncidentFactory incidentFactory,
            PriorityStrategy priorityStrategy,
            SlaCalculator slaCalculator,
            AssignmentStrategy assignmentStrategy,
            CategoryClassifier classifier,
            IncidentPermissionPolicy permissions,
            DomainEventPublisher events,
            Clock clock
    ) {
        if (incidents == null) throw new InvalidInputException("incidents repository must not be null");
        if (users == null) throw new InvalidInputException("users repository must not be null");
        if (services == null) throw new InvalidInputException("services repository must not be null");
        if (categories == null) throw new InvalidInputException("categories repository must not be null");
        if (incidentFactory == null) throw new InvalidInputException("incidentFactory must not be null");
        if (priorityStrategy == null) throw new InvalidInputException("priorityStrategy must not be null");
        if (slaCalculator == null) throw new InvalidInputException("slaCalculator must not be null");
        if (assignmentStrategy == null) throw new InvalidInputException("assignmentStrategy must not be null");
        if (classifier == null) throw new InvalidInputException("classifier must not be null");
        if (permissions == null) throw new InvalidInputException("permissions policy must not be null");
        if (events == null) throw new InvalidInputException("events publisher must not be null");
        if (clock == null) throw new InvalidInputException("clock must not be null");

        this.incidents = incidents;
        this.users = users;
        this.services = services;
        this.categories = categories;
        this.incidentFactory = incidentFactory;
        this.priorityStrategy = priorityStrategy;
        this.slaCalculator = slaCalculator;
        this.assignmentStrategy = assignmentStrategy;
        this.classifier = classifier;
        this.permissions = permissions;
        this.events = events;
        this.clock = clock;
    }

    // 1. Create incident
    public Incident createIncident(CreateIncidentCommand c) {
        if (c == null) throw new InvalidInputException("command must not be null");
        Instant now = clock.instant();

        User actor = loadActiveUser(c.actorId());
        permissions.check(actor, IncidentOperation.CREATE, null);

        ItService service = services.findById(c.serviceId())
                .orElseThrow(() -> new ServiceNotFoundException("Service not found: " + c.serviceId()));

        Category category = categories.findById(c.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + c.categoryId()));

        Category suggestedCategory = null;
        if (c.suggestedCategoryId() != null && !c.suggestedCategoryId().isBlank()) {
            suggestedCategory = categories.findById(c.suggestedCategoryId())
                    .orElseThrow(() -> new CategoryNotFoundException("Suggested category not found: " + c.suggestedCategoryId()));
        }

        IncidentDraft draft = new IncidentDraft(
                c.title(),
                c.description(),
                actor,
                service,
                category,
                suggestedCategory,
                c.categorySource(),
                c.impact(),
                c.urgency(),
                c.affectedUsers()
        );

        Incident incident = incidentFactory.create(draft, now);
        SlaDeadlines deadlines = slaCalculator.calculate(service.getId(), incident.getPriority(), now);
        incident.applySlaDeadlines(deadlines.responseDeadline(), deadlines.resolutionDeadline());
        incident.classify();

        incident = incidents.save(incident);

        events.publish(new IncidentCreated(
                now,
                c.actorId(),
                incident.getId(),
                null,
                IncidentStatus.NEW,
                "Incident created",
                actor.getId(),
                service.getId(),
                category.getId(),
                suggestedCategory != null ? suggestedCategory.getId() : null,
                c.categorySource(),
                incident.getPriority(),
                incident.getPriorityScore()
        ));

        events.publish(new IncidentClassified(
                now,
                c.actorId(),
                incident.getId(),
                IncidentStatus.NEW,
                IncidentStatus.CLASSIFIED,
                "Incident classified",
                category.getId()
        ));

        incident = performAutoAssign(incident, SYSTEM_ACTOR_ID, now);
        return incident;
    }

    // 2. Assign incident
    public Incident assignIncident(AssignIncidentCommand c) {
        if (c == null) throw new InvalidInputException("command must not be null");
        Instant now = clock.instant();

        User actor = loadActiveUser(c.actorId());
        Incident incident = loadIncident(c.incidentId());
        permissions.check(actor, IncidentOperation.ASSIGN, incident);

        incident.ensureAssignable();
        Technician tech = loadAssignableTechnician(c.technicianId());

        incident.assign(tech, now);
        incident = incidents.save(incident);

        events.publish(new IncidentAssigned(
                now,
                c.actorId(),
                incident.getId(),
                IncidentStatus.CLASSIFIED,
                IncidentStatus.ASSIGNED,
                "Assigned to technician " + tech.getId(),
                tech.getId(),
                false
        ));

        return incident;
    }

    // 3. Reassign incident
    public Incident reassignIncident(AssignIncidentCommand c) {
        if (c == null) throw new InvalidInputException("command must not be null");
        Instant now = clock.instant();

        User actor = loadActiveUser(c.actorId());
        Incident incident = loadIncident(c.incidentId());
        permissions.check(actor, IncidentOperation.ASSIGN, incident);

        incident.ensureUnassignable();

        if (incident.getAssignedTechnician().isPresent()
                && incident.getAssignedTechnician().get().getId().equals(c.technicianId())) {
            throw new InvalidInputException("Cannot reassign to the same technician: " + c.technicianId());
        }

        Technician tech = loadAssignableTechnician(c.technicianId());
        IncidentStatus statusBeforeUnassign = incident.getStatus();

        incident.unassign();
        incident.assign(tech, now);
        incident = incidents.save(incident);

        events.publish(new IncidentStatusChanged(
                now,
                c.actorId(),
                incident.getId(),
                statusBeforeUnassign,
                IncidentStatus.CLASSIFIED,
                "Technician unassigned",
                "UNASSIGNED"
        ));

        events.publish(new IncidentAssigned(
                now,
                c.actorId(),
                incident.getId(),
                IncidentStatus.CLASSIFIED,
                IncidentStatus.ASSIGNED,
                "Reassigned to technician " + tech.getId(),
                tech.getId(),
                false
        ));

        return incident;
    }

    // 4. Auto-assign incident
    public Incident autoAssignIncident(String actorId, String incidentId) {
        if (actorId == null || actorId.isBlank()) throw new InvalidInputException("actorId must not be blank");
        if (incidentId == null || incidentId.isBlank()) throw new InvalidInputException("incidentId must not be blank");
        Instant now = clock.instant();

        User actor = loadActiveUser(actorId);
        Incident incident = loadIncident(incidentId);
        permissions.check(actor, IncidentOperation.ASSIGN, incident);

        incident.ensureAssignable();
        return performAutoAssign(incident, actorId, now);
    }

    // 5. Acknowledge incident
    public Incident acknowledgeIncident(String actorId, String incidentId) {
        if (actorId == null || actorId.isBlank()) throw new InvalidInputException("actorId must not be blank");
        if (incidentId == null || incidentId.isBlank()) throw new InvalidInputException("incidentId must not be blank");
        Instant now = clock.instant();

        User actor = loadActiveUser(actorId);
        Incident incident = loadIncident(incidentId);
        permissions.check(actor, IncidentOperation.ACKNOWLEDGE, incident);

        incident.acknowledge(now);
        incident = incidents.save(incident);

        events.publish(new IncidentAcknowledged(
                now,
                actorId,
                incident.getId(),
                IncidentStatus.ASSIGNED,
                IncidentStatus.ACKNOWLEDGED,
                "Incident acknowledged by technician",
                actorId
        ));

        return incident;
    }

    // 6. Start work
    public Incident startWork(String actorId, String incidentId) {
        if (actorId == null || actorId.isBlank()) throw new InvalidInputException("actorId must not be blank");
        if (incidentId == null || incidentId.isBlank()) throw new InvalidInputException("incidentId must not be blank");
        Instant now = clock.instant();

        User actor = loadActiveUser(actorId);
        Incident incident = loadIncident(incidentId);
        permissions.check(actor, IncidentOperation.START_WORK, incident);

        incident.startWork();
        incident = incidents.save(incident);

        events.publish(new IncidentStatusChanged(
                now,
                actorId,
                incident.getId(),
                IncidentStatus.ACKNOWLEDGED,
                IncidentStatus.IN_PROGRESS,
                "Work started on incident",
                "WORK_STARTED"
        ));

        return incident;
    }

    // 7. Add investigation note
    public Incident addInvestigationNote(IncidentTextCommand c) {
        if (c == null) throw new InvalidInputException("command must not be null");
        Instant now = clock.instant();

        User actor = loadActiveUser(c.actorId());
        Incident incident = loadIncident(c.incidentId());
        permissions.check(actor, IncidentOperation.ADD_INVESTIGATION_NOTE, incident);

        incident.addInvestigationNote(c.actorId(), c.text(), now);
        incident = incidents.save(incident);

        events.publish(new IncidentUpdated(
                now,
                c.actorId(),
                incident.getId(),
                incident.getStatus(),
                incident.getStatus(),
                "Investigation note added",
                "NOTE_ADDED"
        ));

        return incident;
    }

    // 8. Request info
    public Incident requestInfo(IncidentTextCommand c) {
        if (c == null) throw new InvalidInputException("command must not be null");
        Instant now = clock.instant();

        User actor = loadActiveUser(c.actorId());
        Incident incident = loadIncident(c.incidentId());
        permissions.check(actor, IncidentOperation.REQUEST_INFO, incident);

        incident.requestInfo(c.actorId(), c.text(), now);
        incident = incidents.save(incident);

        events.publish(new IncidentStatusChanged(
                now,
                c.actorId(),
                incident.getId(),
                IncidentStatus.IN_PROGRESS,
                IncidentStatus.WAITING_FOR_USER,
                "Information requested from user",
                "INFO_REQUESTED"
        ));

        return incident;
    }

    // 9. Respond to info request
    public Incident respondToInfoRequest(IncidentTextCommand c) {
        if (c == null) throw new InvalidInputException("command must not be null");
        Instant now = clock.instant();

        User actor = loadActiveUser(c.actorId());
        Incident incident = loadIncident(c.incidentId());
        permissions.check(actor, IncidentOperation.RESPOND_INFO, incident);

        incident.receiveInfo(c.actorId(), c.text(), now);
        incident = incidents.save(incident);

        events.publish(new IncidentStatusChanged(
                now,
                c.actorId(),
                incident.getId(),
                IncidentStatus.WAITING_FOR_USER,
                IncidentStatus.IN_PROGRESS,
                "Information received from user",
                "INFO_RECEIVED"
        ));

        return incident;
    }

    // 10. Add additional info
    public Incident addAdditionalInfo(IncidentTextCommand c) {
        if (c == null) throw new InvalidInputException("command must not be null");
        Instant now = clock.instant();

        User actor = loadActiveUser(c.actorId());
        Incident incident = loadIncident(c.incidentId());
        permissions.check(actor, IncidentOperation.ADD_ADDITIONAL_INFO, incident);

        incident.addAdditionalInfo(c.actorId(), c.text(), now);
        incident = incidents.save(incident);

        events.publish(new IncidentUpdated(
                now,
                c.actorId(),
                incident.getId(),
                incident.getStatus(),
                incident.getStatus(),
                "Additional info added",
                "ADDITIONAL_INFO_ADDED"
        ));

        return incident;
    }

    // 11. Resolve incident
    public Incident resolveIncident(IncidentTextCommand c) {
        if (c == null) throw new InvalidInputException("command must not be null");
        Instant now = clock.instant();

        User actor = loadActiveUser(c.actorId());
        Incident incident = loadIncident(c.incidentId());
        permissions.check(actor, IncidentOperation.RESOLVE, incident);

        incident.resolve(c.actorId(), c.text(), now);
        incident = incidents.save(incident);

        events.publish(new IncidentResolved(
                now,
                c.actorId(),
                incident.getId(),
                IncidentStatus.IN_PROGRESS,
                IncidentStatus.RESOLVED,
                "Incident resolved",
                c.actorId()
        ));

        return incident;
    }

    // 12. Verify resolution
    public Incident verifyResolution(String actorId, String incidentId) {
        if (actorId == null || actorId.isBlank()) throw new InvalidInputException("actorId must not be blank");
        if (incidentId == null || incidentId.isBlank()) throw new InvalidInputException("incidentId must not be blank");
        Instant now = clock.instant();

        User actor = loadActiveUser(actorId);
        Incident incident = loadIncident(incidentId);
        permissions.check(actor, IncidentOperation.VERIFY, incident);

        incident.verify(now);
        incident = incidents.save(incident);

        events.publish(new IncidentStatusChanged(
                now,
                actorId,
                incident.getId(),
                IncidentStatus.RESOLVED,
                IncidentStatus.VERIFIED,
                "Resolution verified",
                "VERIFIED"
        ));

        return incident;
    }

    // 13. Reject resolution
    public Incident rejectResolution(String actorId, String incidentId) {
        if (actorId == null || actorId.isBlank()) throw new InvalidInputException("actorId must not be blank");
        if (incidentId == null || incidentId.isBlank()) throw new InvalidInputException("incidentId must not be blank");
        Instant now = clock.instant();

        User actor = loadActiveUser(actorId);
        Incident incident = loadIncident(incidentId);
        permissions.check(actor, IncidentOperation.REJECT_RESOLUTION, incident);

        incident.rejectResolution();
        // Leave a visible trace in the incident history (employee and technician see notes; audit is admin-only)
        incident.addAdditionalInfo(actorId, REJECTION_NOTE, now);
        incident = incidents.save(incident);

        events.publish(new IncidentStatusChanged(
                now,
                actorId,
                incident.getId(),
                IncidentStatus.RESOLVED,
                IncidentStatus.IN_PROGRESS,
                "Resolution rejected",
                "RESOLUTION_REJECTED"
        ));

        return incident;
    }

    // 14. Close incident
    public Incident closeIncident(CloseIncidentCommand c) {
        if (c == null) throw new InvalidInputException("command must not be null");
        Instant now = clock.instant();

        User actor = loadActiveUser(c.actorId());
        Incident incident = loadIncident(c.incidentId());
        permissions.check(actor, IncidentOperation.CLOSE, incident);

        incident.close(c.actorId(), c.closureNote(), now);
        incident = incidents.save(incident);

        events.publish(new IncidentClosed(
                now,
                c.actorId(),
                incident.getId(),
                IncidentStatus.VERIFIED,
                IncidentStatus.CLOSED,
                "Incident closed",
                c.actorId()
        ));

        return incident;
    }

    // 15. Submit feedback
    public Incident submitFeedback(SubmitFeedbackCommand c) {
        if (c == null) throw new InvalidInputException("command must not be null");
        Instant now = clock.instant();

        User actor = loadActiveUser(c.actorId());
        Incident incident = loadIncident(c.incidentId());
        permissions.check(actor, IncidentOperation.SUBMIT_FEEDBACK, incident);

        incident.submitFeedback(c.actorId(), c.rating(), c.comment(), now);
        incident = incidents.save(incident);

        events.publish(new IncidentUpdated(
                now,
                c.actorId(),
                incident.getId(),
                incident.getStatus(),
                incident.getStatus(),
                "Feedback submitted",
                "FEEDBACK_SUBMITTED"
        ));

        return incident;
    }

    // 16. Update priority inputs
    public Incident updatePriorityInputs(UpdatePriorityInputsCommand c) {
        if (c == null) throw new InvalidInputException("command must not be null");
        Instant now = clock.instant();

        User actor = loadActiveUser(c.actorId());
        Incident incident = loadIncident(c.incidentId());
        permissions.check(actor, IncidentOperation.UPDATE_PRIORITY, incident);

        if (incident.getImpact() == c.impact() && incident.getUrgency() == c.urgency()) {
            return incident;
        }

        PriorityInput input = new PriorityInput(
                c.impact(),
                c.urgency(),
                incident.getService().getBusinessCriticality(),
                incident.getAffectedUsers()
        );
        PriorityResult result = priorityStrategy.calculate(input);

        SlaDeadlines newDeadlines = slaCalculator.calculate(
                incident.getService().getId(),
                result.level(),
                incident.getCreatedAt()
        );

        Instant responseDeadline = incident.getAcknowledgedAt().isPresent()
                ? incident.getResponseDeadline().orElse(newDeadlines.responseDeadline())
                : newDeadlines.responseDeadline();
        Instant resolutionDeadline = newDeadlines.resolutionDeadline();
        if (resolutionDeadline.isBefore(responseDeadline)) {
            resolutionDeadline = responseDeadline;
        }

        PriorityLevel prevPriority = incident.getPriority();
        int prevScore = incident.getPriorityScore();

        incident.updatePriority(c.impact(), c.urgency(), result);
        incident.applySlaDeadlines(responseDeadline, resolutionDeadline);

        incident = incidents.save(incident);

        events.publish(new PriorityChanged(
                now,
                c.actorId(),
                incident.getId(),
                incident.getStatus(),
                incident.getStatus(),
                "Priority updated",
                prevPriority,
                result.level(),
                prevScore,
                result.score()
        ));

        return incident;
    }

    // 17. Get incident
    public Incident getIncident(String actorId, String incidentId) {
        if (actorId == null || actorId.isBlank()) throw new InvalidInputException("actorId must not be blank");
        if (incidentId == null || incidentId.isBlank()) throw new InvalidInputException("incidentId must not be blank");

        User actor = loadActiveUser(actorId);
        Incident incident = loadIncident(incidentId);
        permissions.check(actor, IncidentOperation.VIEW, incident);

        return incident;
    }

    // 18. List visible incidents
    public List<Incident> listVisibleIncidents(String actorId) {
        if (actorId == null || actorId.isBlank()) throw new InvalidInputException("actorId must not be blank");

        User actor = loadActiveUser(actorId);
        return switch (actor.getRole()) {
            case EMPLOYEE -> incidents.findByReporterId(actor.getId());
            case TECHNICIAN, TEAM_LEAD -> incidents.findByAssignedTechnicianId(actor.getId());
            case ADMINISTRATOR, IT_MANAGER -> incidents.findAll();
        };
    }

    // 19. Suggest category
    public Optional<CategorySuggestion> suggestCategory(String actorId, String title, String description) {
        if (actorId == null || actorId.isBlank()) throw new InvalidInputException("actorId must not be blank");

        User actor = loadActiveUser(actorId);
        permissions.check(actor, IncidentOperation.SUGGEST_CATEGORY, null);

        Optional<CategorySuggestion> suggestion = classifier.classify(title, description);
        if (suggestion.isEmpty()) {
            return Optional.empty();
        }

        Optional<Category> category = categories.findById(suggestion.get().categoryId());
        if (category.isEmpty() || !category.get().isActive()) {
            return Optional.empty();
        }

        return suggestion;
    }

    // Private auto-assign helper (Q.5)
    private Incident performAutoAssign(Incident incident, String actorId, Instant now) {
        List<Technician> allTechnicians = users.findAllTechnicians();
        Map<String, Integer> workload = new HashMap<>();
        List<Technician> candidates = new ArrayList<>();

        for (Technician tech : allTechnicians) {
            int active = incidents.countActiveByTechnicianId(tech.getId());
            workload.put(tech.getId(), active);
            if (tech.isAssignable() && !tech.isOverloaded(active)) {
                candidates.add(tech);
            }
        }

        if (candidates.isEmpty()) {
            events.publish(new AssignmentFailed(
                    now,
                    actorId,
                    incident.getId(),
                    IncidentStatus.CLASSIFIED,
                    IncidentStatus.CLASSIFIED,
                    "No available technician"
            ));
            return incident;
        }

        Optional<Technician> pick = assignmentStrategy.pick(incident, candidates, workload);
        if (pick.isEmpty()) {
            events.publish(new AssignmentFailed(
                    now,
                    actorId,
                    incident.getId(),
                    IncidentStatus.CLASSIFIED,
                    IncidentStatus.CLASSIFIED,
                    "Strategy " + assignmentStrategy.name() + " returned no technician"
            ));
            return incident;
        }

        Technician chosen = pick.get();
        incident.assign(chosen, now);
        incident = incidents.save(incident);

        events.publish(new IncidentAssigned(
                now,
                actorId,
                incident.getId(),
                IncidentStatus.CLASSIFIED,
                IncidentStatus.ASSIGNED,
                "Incident automatically assigned to " + chosen.getId(),
                chosen.getId(),
                true
        ));

        return incident;
    }

    private User loadActiveUser(String userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        if (!user.isActive()) {
            throw new UnauthorizedOperationException("User is inactive: " + userId);
        }
        return user;
    }

    private Incident loadIncident(String incidentId) {
        return incidents.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException("Incident not found: " + incidentId));
    }

    private Technician loadAssignableTechnician(String technicianId) {
        User user = users.findById(technicianId)
                .orElseThrow(() -> new UserNotFoundException("Technician not found: " + technicianId));
        if (!(user instanceof Technician tech)) {
            throw new InvalidInputException("User is not a technician: " + technicianId);
        }
        if (!tech.isAssignable()) {
            throw new TechnicianUnavailableException("Technician " + technicianId + " is not assignable");
        }
        int active = incidents.countActiveByTechnicianId(technicianId);
        if (tech.isOverloaded(active)) {
            throw new TechnicianOverloadedException("Technician " + technicianId + " workload exceeded");
        }
        return tech;
    }
}
