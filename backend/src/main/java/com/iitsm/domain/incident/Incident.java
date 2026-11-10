package com.iitsm.domain.incident;

import com.iitsm.domain.exception.InvalidIncidentException;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.model.NoteType;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.model.User;
import com.iitsm.domain.priority.PriorityResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Aggregate root for an IT service management incident.
 * Owns its data and enforces invariants; delegates lifecycle transition legality to its {@link IncidentState}.
 */
public final class Incident {

    private static final int MAX_TEXT_LENGTH = 4000;
    private static final int MAX_COMMENT_LENGTH = 1000;

    // ---- identity / immutable core ----
    final String id;
    final String title;
    final String description;
    final User reporter;
    final ItService service;
    final Instant createdAt;

    // ---- mutable classification ----
    Category category;
    Category suggestedCategory;    // nullable
    CategorySource categorySource;

    // ---- mutable priority ----
    Impact impact;
    Urgency urgency;
    final int affectedUsers;
    PriorityLevel priority;
    int priorityScore;

    // ---- state machine ----
    IncidentState state;           // never null

    // ---- assignment ----
    Technician assignedTechnician; // nullable
    Instant assignedAt;            // nullable
    Instant acknowledgedAt;        // nullable – FIRST acknowledgement only

    // ---- resolution / lifecycle timestamps ----
    Instant verifiedAt;            // nullable
    Instant closedAt;              // nullable
    String closedById;             // nullable
    String closureNote;            // nullable

    // ---- SLA ----
    Instant responseDeadline;      // nullable until applied
    Instant resolutionDeadline;    // nullable until applied; base, excludes pauses
    Instant waitingSince;          // nullable
    Duration pausedDuration = Duration.ZERO;

    // ---- notes / resolution / feedback ----
    final List<IncidentNote> notes = new ArrayList<>();
    Resolution resolution;         // nullable
    Feedback feedback;             // nullable

    /**
     * Creates a new Incident from a validated draft and computed priority.
     * Initial status is NEW; no assignee, no deadlines.
     */
    public Incident(String id, IncidentDraft draft, PriorityResult priority, Instant createdAt) {
        if (id == null || id.trim().isEmpty()) {
            throw new InvalidInputException("id must not be blank");
        }
        if (draft == null) {
            throw new InvalidInputException("draft must not be null");
        }
        if (priority == null) {
            throw new InvalidInputException("priority must not be null");
        }
        if (createdAt == null) {
            throw new InvalidInputException("createdAt must not be null");
        }

        this.id = id.trim();
        this.title = draft.title();
        this.description = draft.description();
        this.reporter = draft.reporter();
        this.service = draft.service();
        this.category = draft.category();
        this.suggestedCategory = draft.suggestedCategory();
        this.categorySource = draft.categorySource();
        this.impact = draft.impact();
        this.urgency = draft.urgency();
        this.affectedUsers = draft.affectedUsers();
        this.priority = priority.level();
        this.priorityScore = priority.score();
        this.createdAt = createdAt;
        this.state = IncidentStates.initial();   // NEW
    }

    // ===== Getters =====

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public User getReporter() { return reporter; }
    public ItService getService() { return service; }

    public Category getCategory() { return category; }
    public Optional<Category> getSuggestedCategory() { return Optional.ofNullable(suggestedCategory); }
    public CategorySource getCategorySource() { return categorySource; }

    public Impact getImpact() { return impact; }
    public Urgency getUrgency() { return urgency; }
    public int getAffectedUsers() { return affectedUsers; }
    public PriorityLevel getPriority() { return priority; }
    public int getPriorityScore() { return priorityScore; }

    public Instant getCreatedAt() { return createdAt; }

    /** Current lifecycle status (derived from state). */
    public IncidentStatus getStatus() { return state.status(); }

    public Optional<Technician> getAssignedTechnician() { return Optional.ofNullable(assignedTechnician); }
    public Optional<Instant> getAssignedAt() { return Optional.ofNullable(assignedAt); }
    public Optional<Instant> getAcknowledgedAt() { return Optional.ofNullable(acknowledgedAt); }

    /** Resolved-at time, taken from the Resolution record. */
    public Optional<Instant> getResolvedAt() {
        return resolution == null ? Optional.empty() : Optional.of(resolution.resolvedAt());
    }

    public Optional<Instant> getVerifiedAt() { return Optional.ofNullable(verifiedAt); }
    public Optional<Instant> getClosedAt() { return Optional.ofNullable(closedAt); }
    public Optional<String> getClosedById() { return Optional.ofNullable(closedById); }
    public Optional<String> getClosureNote() { return Optional.ofNullable(closureNote); }

    public Optional<Instant> getResponseDeadline() { return Optional.ofNullable(responseDeadline); }
    public Optional<Instant> getResolutionDeadline() { return Optional.ofNullable(resolutionDeadline); }
    public Optional<Instant> getWaitingSince() { return Optional.ofNullable(waitingSince); }
    public Duration getPausedDuration() { return pausedDuration; }

    /** Unmodifiable view of notes in insertion order. */
    public List<IncidentNote> getNotes() { return Collections.unmodifiableList(notes); }

    public Optional<Resolution> getResolution() { return Optional.ofNullable(resolution); }
    public Optional<Feedback> getFeedback() { return Optional.ofNullable(feedback); }

    // ===== Behaviour methods =====

    /**
     * Sets SLA response and resolution deadlines.
     * Both must be non-null; createdAt ≤ responseDeadline ≤ resolutionDeadline.
     * No state guard — may be called by the service at creation or after a priority change.
     */
    public void applySlaDeadlines(Instant responseDeadline, Instant resolutionDeadline) {
        if (responseDeadline == null) {
            throw new InvalidInputException("responseDeadline must not be null");
        }
        if (resolutionDeadline == null) {
            throw new InvalidInputException("resolutionDeadline must not be null");
        }
        if (responseDeadline.isBefore(createdAt)) {
            throw new InvalidInputException("responseDeadline must not be before createdAt");
        }
        if (resolutionDeadline.isBefore(responseDeadline)) {
            throw new InvalidInputException("resolutionDeadline must not be before responseDeadline");
        }
        this.responseDeadline = responseDeadline;
        this.resolutionDeadline = resolutionDeadline;
    }

    /** Transitions NEW → CLASSIFIED. */
    public void classify() {
        this.state = state.classify();
    }

    /**
     * Pure check: asks the state if assignment is legal (CLASSIFIED → ASSIGNED).
     * Discards the returned next-state; throws {@link com.iitsm.domain.exception.InvalidStateTransitionException} on failure.
     */
    public void ensureAssignable() {
        state.assign();   // throws if not legal; return value discarded
    }

    /**
     * Pure check: asks the state if unassignment is legal (ASSIGNED/ACKNOWLEDGED → CLASSIFIED).
     * Discards the returned next-state; throws on failure.
     */
    public void ensureUnassignable() {
        state.unassign(); // throws if not legal; return value discarded
    }

    /** Assigns the given technician; transitions CLASSIFIED → ASSIGNED. */
    public void assign(Technician technician, Instant now) {
        if (technician == null) {
            throw new InvalidInputException("technician must not be null");
        }
        if (now == null) {
            throw new InvalidInputException("now must not be null");
        }
        IncidentState next = state.assign();
        this.assignedTechnician = technician;
        this.assignedAt = now;
        this.state = next;
    }

    /** Removes the assigned technician; transitions ASSIGNED/ACKNOWLEDGED → CLASSIFIED. acknowledgedAt is kept. */
    public void unassign() {
        IncidentState next = state.unassign();
        this.assignedTechnician = null;
        this.assignedAt = null;
        // acknowledgedAt intentionally kept per §J.3
        this.state = next;
    }

    /**
     * Transitions ASSIGNED → ACKNOWLEDGED.
     * Sets acknowledgedAt only if it has not been set before (first acknowledgement only).
     */
    public void acknowledge(Instant now) {
        if (now == null) {
            throw new InvalidInputException("now must not be null");
        }
        IncidentState next = state.acknowledge();
        if (this.acknowledgedAt == null) {
            this.acknowledgedAt = now;
        }
        this.state = next;
    }

    /** Transitions ACKNOWLEDGED → IN_PROGRESS. */
    public void startWork() {
        this.state = state.startWork();
    }

    /**
     * Transitions IN_PROGRESS → WAITING_FOR_USER.
     * Records waitingSince and adds an INFO_REQUEST note.
     */
    public void requestInfo(String authorId, String question, Instant now) {
        authorId = requireNonBlank(authorId, "authorId");
        question = requireNonBlankMaxLength(question, "question", MAX_TEXT_LENGTH);
        if (now == null) {
            throw new InvalidInputException("now must not be null");
        }
        IncidentState next = state.requestInfo();
        this.waitingSince = now;
        notes.add(new IncidentNote(authorId, NoteType.INFO_REQUEST, question, now));
        this.state = next;
    }

    /**
     * Transitions WAITING_FOR_USER → IN_PROGRESS.
     * Folds the paused duration and adds an INFO_RESPONSE note.
     */
    public void receiveInfo(String authorId, String response, Instant now) {
        authorId = requireNonBlank(authorId, "authorId");
        response = requireNonBlankMaxLength(response, "response", MAX_TEXT_LENGTH);
        if (now == null) {
            throw new InvalidInputException("now must not be null");
        }
        IncidentState next = state.receiveInfo();
        if (waitingSince != null) {
            long waitedNanos = Duration.between(waitingSince, now).toNanos();
            if (waitedNanos > 0) {
                pausedDuration = pausedDuration.plusNanos(waitedNanos);
            }
        }
        this.waitingSince = null;
        notes.add(new IncidentNote(authorId, NoteType.INFO_RESPONSE, response, now));
        this.state = next;
    }

    /**
     * Transitions IN_PROGRESS → RESOLVED.
     * Records the resolution.
     */
    public void resolve(String resolverId, String summary, Instant now) {
        resolverId = requireNonBlank(resolverId, "resolverId");
        summary = requireNonBlankMaxLength(summary, "summary", MAX_TEXT_LENGTH);
        if (now == null) {
            throw new InvalidInputException("now must not be null");
        }
        IncidentState next = state.resolve();
        this.resolution = new Resolution(resolverId, summary, now);
        this.state = next;
    }

    /**
     * Transitions RESOLVED → IN_PROGRESS (reopen).
     * Clears the resolution record.
     */
    public void rejectResolution() {
        IncidentState next = state.rejectResolution();
        this.resolution = null;
        this.state = next;
    }

    /** Transitions RESOLVED → VERIFIED. Sets verifiedAt. */
    public void verify(Instant now) {
        if (now == null) {
            throw new InvalidInputException("now must not be null");
        }
        IncidentState next = state.verify();
        this.verifiedAt = now;
        this.state = next;
    }

    /**
     * Transitions VERIFIED → CLOSED.
     * Sets closedById, closureNote (blank → null), closedAt.
     */
    public void close(String closedById, String closureNote, Instant now) {
        closedById = requireNonBlank(closedById, "closedById");
        if (now == null) {
            throw new InvalidInputException("now must not be null");
        }
        IncidentState next = state.close();
        this.closedById = closedById;
        // blank closureNote stored as null per contract
        this.closureNote = (closureNote == null || closureNote.trim().isEmpty()) ? null : closureNote.trim();
        this.closedAt = now;
        this.state = next;
    }

    /**
     * Adds an INVESTIGATION note. Guard: state.allowsInvestigationNotes().
     */
    public void addInvestigationNote(String authorId, String text, Instant now) {
        authorId = requireNonBlank(authorId, "authorId");
        text = requireNonBlankMaxLength(text, "text", MAX_TEXT_LENGTH);
        if (now == null) {
            throw new InvalidInputException("now must not be null");
        }
        if (!state.allowsInvestigationNotes()) {
            throw new InvalidIncidentException(
                    "Cannot add investigation note to an incident that is " + state.status());
        }
        notes.add(new IncidentNote(authorId, NoteType.INVESTIGATION, text, now));
    }

    /**
     * Adds an ADDITIONAL_INFO note. Guard: state.allowsAdditionalInfo().
     */
    public void addAdditionalInfo(String authorId, String text, Instant now) {
        authorId = requireNonBlank(authorId, "authorId");
        text = requireNonBlankMaxLength(text, "text", MAX_TEXT_LENGTH);
        if (now == null) {
            throw new InvalidInputException("now must not be null");
        }
        if (!state.allowsAdditionalInfo()) {
            throw new InvalidIncidentException(
                    "Cannot add additional info to an incident that is " + state.status());
        }
        notes.add(new IncidentNote(authorId, NoteType.ADDITIONAL_INFO, text, now));
    }

    /**
     * Submits reporter feedback. Guards: state.allowsFeedback() and no existing feedback.
     * Rating must be 1–5; comment may be null.
     */
    public void submitFeedback(String authorId, int rating, String comment, Instant now) {
        authorId = requireNonBlank(authorId, "authorId");
        if (now == null) {
            throw new InvalidInputException("now must not be null");
        }
        if (!state.allowsFeedback()) {
            throw new InvalidIncidentException(
                    "Cannot submit feedback for an incident that is " + state.status());
        }
        if (this.feedback != null) {
            throw new InvalidIncidentException("Feedback has already been submitted for this incident");
        }
        if (rating < 1 || rating > 5) {
            throw new InvalidInputException("rating must be between 1 and 5");
        }
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new InvalidInputException("comment must not exceed " + MAX_COMMENT_LENGTH + " characters");
        }
        this.feedback = new Feedback(authorId, rating, comment, now);
    }

    /**
     * Updates impact, urgency, priority score and level.
     * Guard: state.allowsPriorityChange().
     */
    public void updatePriority(Impact impact, Urgency urgency, PriorityResult result) {
        if (impact == null) {
            throw new InvalidInputException("impact must not be null");
        }
        if (urgency == null) {
            throw new InvalidInputException("urgency must not be null");
        }
        if (result == null) {
            throw new InvalidInputException("result must not be null");
        }
        if (!state.allowsPriorityChange()) {
            throw new InvalidIncidentException(
                    "Cannot update priority of an incident that is " + state.status());
        }
        this.impact = impact;
        this.urgency = urgency;
        this.priority = result.level();
        this.priorityScore = result.score();
    }

    // ===== Private helpers =====

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidInputException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String requireNonBlankMaxLength(String value, String field, int maxLength) {
        String trimmed = requireNonBlank(value, field);
        if (trimmed.length() > maxLength) {
            throw new InvalidInputException(field + " must not exceed " + maxLength + " characters");
        }
        return trimmed;
    }
}

