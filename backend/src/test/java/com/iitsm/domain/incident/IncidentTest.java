package com.iitsm.domain.incident;

import com.iitsm.domain.exception.InvalidIncidentException;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.InvalidStateTransitionException;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.model.NoteType;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.priority.PriorityResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IncidentTest {

    private static final Instant T0 = Instant.parse("2026-11-16T09:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final Instant T2 = T0.plusSeconds(120);
    private static final Instant T3 = T0.plusSeconds(180);
    private static final Instant T4 = T0.plusSeconds(240);
    private static final Instant T5 = T0.plusSeconds(300);

    private Employee reporter;
    private ItService service;
    private Category category;
    private PriorityResult priority;

    @BeforeEach
    void setUp() {
        reporter = new Employee("U1", "Alice", "alice@corp.com", "IT");
        service  = new ItService("SVC1", "Network", BusinessCriticality.HIGH);
        category = new Category("NETWORK", "Network Issues");
        priority = new PriorityResult(27, PriorityLevel.P2);
    }

    private IncidentDraft draft() {
        return new IncidentDraft(
                "Cannot connect to VPN",
                "VPN login fails for all employees.",
                reporter, service, category,
                null, CategorySource.MANUAL,
                Impact.HIGH, Urgency.HIGH, 1
        );
    }

    private Incident newIncident() {
        return new Incident("INC1001", draft(), priority, T0);
    }

    private Technician technician(String id) {
        return new Technician(id, "Tech " + id, id.toLowerCase() + "@corp.com",
                Role.TECHNICIAN, Map.of("NETWORK", 3), 5, 10);
    }

    // ===== Constructor invariants =====

    @Test
    void constructor_blankId_throwsInvalidInput() {
        assertThrows(InvalidInputException.class,
                () -> new Incident("", draft(), priority, T0));
        assertThrows(InvalidInputException.class,
                () -> new Incident(null, draft(), priority, T0));
        assertThrows(InvalidInputException.class,
                () -> new Incident("  ", draft(), priority, T0));
    }

    @Test
    void constructor_nullDraft_throwsInvalidInput() {
        assertThrows(InvalidInputException.class,
                () -> new Incident("INC1001", null, priority, T0));
    }

    @Test
    void constructor_nullPriority_throwsInvalidInput() {
        assertThrows(InvalidInputException.class,
                () -> new Incident("INC1001", draft(), null, T0));
    }

    @Test
    void constructor_nullCreatedAt_throwsInvalidInput() {
        assertThrows(InvalidInputException.class,
                () -> new Incident("INC1001", draft(), priority, null));
    }

    @Test
    void constructor_validArgs_setsAllFieldsCorrectly() {
        Incident inc = newIncident();

        assertEquals("INC1001", inc.getId());
        assertEquals("Cannot connect to VPN", inc.getTitle());
        assertEquals("VPN login fails for all employees.", inc.getDescription());
        assertEquals(reporter, inc.getReporter());
        assertEquals(service, inc.getService());
        assertEquals(category, inc.getCategory());
        assertTrue(inc.getSuggestedCategory().isEmpty());
        assertEquals(CategorySource.MANUAL, inc.getCategorySource());
        assertEquals(Impact.HIGH, inc.getImpact());
        assertEquals(Urgency.HIGH, inc.getUrgency());
        assertEquals(1, inc.getAffectedUsers());
        assertEquals(27, inc.getPriorityScore());
        assertEquals(PriorityLevel.P2, inc.getPriority());
        assertEquals(T0, inc.getCreatedAt());
        assertEquals(IncidentStatus.NEW, inc.getStatus());
    }

    @Test
    void constructor_initialStateHasNoAssigneeOrDeadlines() {
        Incident inc = newIncident();

        assertTrue(inc.getAssignedTechnician().isEmpty());
        assertTrue(inc.getAssignedAt().isEmpty());
        assertTrue(inc.getAcknowledgedAt().isEmpty());
        assertTrue(inc.getResponseDeadline().isEmpty());
        assertTrue(inc.getResolutionDeadline().isEmpty());
        assertTrue(inc.getResolution().isEmpty());
        assertTrue(inc.getFeedback().isEmpty());
        assertTrue(inc.getNotes().isEmpty());
        assertEquals(Duration.ZERO, inc.getPausedDuration());
    }

    // ===== IncidentDraft validation =====

    @Test
    void draft_blankTitle_throwsInvalidInput() {
        assertThrows(InvalidInputException.class, () ->
                new IncidentDraft("", "desc", reporter, service, category,
                        null, CategorySource.MANUAL, Impact.LOW, Urgency.LOW, 1));
        assertThrows(InvalidInputException.class, () ->
                new IncidentDraft(null, "desc", reporter, service, category,
                        null, CategorySource.MANUAL, Impact.LOW, Urgency.LOW, 1));
    }

    @Test
    void draft_titleTooLong_throwsInvalidInput() {
        String longTitle = "A".repeat(201);
        assertThrows(InvalidInputException.class, () ->
                new IncidentDraft(longTitle, "desc", reporter, service, category,
                        null, CategorySource.MANUAL, Impact.LOW, Urgency.LOW, 1));
    }

    @Test
    void draft_descriptionTooLong_throwsInvalidInput() {
        String longDesc = "A".repeat(4001);
        assertThrows(InvalidInputException.class, () ->
                new IncidentDraft("Title", longDesc, reporter, service, category,
                        null, CategorySource.MANUAL, Impact.LOW, Urgency.LOW, 1));
    }

    @Test
    void draft_affectedUsersZero_throwsInvalidInput() {
        assertThrows(InvalidInputException.class, () ->
                new IncidentDraft("Title", "desc", reporter, service, category,
                        null, CategorySource.MANUAL, Impact.LOW, Urgency.LOW, 0));
    }

    @Test
    void draft_inactiveCategory_throwsInvalidInput() {
        category.deactivate();
        assertThrows(InvalidInputException.class, () ->
                new IncidentDraft("Title", "desc", reporter, service, category,
                        null, CategorySource.MANUAL, Impact.LOW, Urgency.LOW, 1));
    }

    @Test
    void draft_keywordSourceWithoutSuggestedCategory_throwsInvalidInput() {
        assertThrows(InvalidInputException.class, () ->
                new IncidentDraft("Title", "desc", reporter, service, category,
                        null, CategorySource.KEYWORD, Impact.LOW, Urgency.LOW, 1));
    }

    @Test
    void draft_keywordSourceWithMismatchedSuggestedCategory_throwsInvalidInput() {
        Category other = new Category("SOFTWARE", "Software");
        assertThrows(InvalidInputException.class, () ->
                new IncidentDraft("Title", "desc", reporter, service, category,
                        other, CategorySource.KEYWORD, Impact.LOW, Urgency.LOW, 1));
    }

    @Test
    void draft_keywordSourceWithMatchingSuggestedCategory_accepted() {
        Category suggestion = new Category("NETWORK", "Network (suggested)");
        assertDoesNotThrow(() ->
                new IncidentDraft("Title", "desc", reporter, service, category,
                        suggestion, CategorySource.KEYWORD, Impact.LOW, Urgency.LOW, 1));
    }

    @Test
    void draft_manualSourceWithSuggestedCategory_accepted() {
        Category suggestion = new Category("SOFTWARE", "Software (override)");
        // MANUAL allows suggestedCategory to differ (reporter overrode suggestion)
        assertDoesNotThrow(() ->
                new IncidentDraft("Title", "desc", reporter, service, category,
                        suggestion, CategorySource.MANUAL, Impact.LOW, Urgency.LOW, 1));
    }

    // ===== applySlaDeadlines =====

    @Test
    void applySlaDeadlines_validDeadlines_setsFields() {
        Incident inc = newIncident();
        Instant response = T0.plusSeconds(300);
        Instant resolution = T0.plusSeconds(1800);

        inc.applySlaDeadlines(response, resolution);

        assertEquals(response, inc.getResponseDeadline().orElseThrow());
        assertEquals(resolution, inc.getResolutionDeadline().orElseThrow());
    }

    @Test
    void applySlaDeadlines_nullResponseDeadline_throwsInvalidInput() {
        Incident inc = newIncident();
        assertThrows(InvalidInputException.class,
                () -> inc.applySlaDeadlines(null, T0.plusSeconds(1800)));
    }

    @Test
    void applySlaDeadlines_nullResolutionDeadline_throwsInvalidInput() {
        Incident inc = newIncident();
        assertThrows(InvalidInputException.class,
                () -> inc.applySlaDeadlines(T0.plusSeconds(300), null));
    }

    @Test
    void applySlaDeadlines_responseBeforeCreatedAt_throwsInvalidInput() {
        Incident inc = newIncident();
        assertThrows(InvalidInputException.class,
                () -> inc.applySlaDeadlines(T0.minusSeconds(1), T0.plusSeconds(1800)));
    }

    @Test
    void applySlaDeadlines_resolutionBeforeResponse_throwsInvalidInput() {
        Incident inc = newIncident();
        assertThrows(InvalidInputException.class,
                () -> inc.applySlaDeadlines(T0.plusSeconds(600), T0.plusSeconds(300)));
    }

    @Test
    void applySlaDeadlines_canBeCalledAgainAfterPriorityChange() {
        Incident inc = newIncident();
        inc.applySlaDeadlines(T0.plusSeconds(300), T0.plusSeconds(1800));

        // simulate priority change recalculation
        Instant newResponse = T0.plusSeconds(900);
        Instant newResolution = T0.plusSeconds(28800);
        inc.applySlaDeadlines(newResponse, newResolution);

        assertEquals(newResponse, inc.getResponseDeadline().orElseThrow());
        assertEquals(newResolution, inc.getResolutionDeadline().orElseThrow());
    }

    // ===== State integration: classify / assign / acknowledge / startWork =====

    @Test
    void classify_fromNew_transitionsToClassified() {
        Incident inc = newIncident();
        inc.classify();
        assertEquals(IncidentStatus.CLASSIFIED, inc.getStatus());
    }

    @Test
    void classify_fromClassified_throwsInvalidStateTransition() {
        Incident inc = newIncident();
        inc.classify();
        assertThrows(InvalidStateTransitionException.class, inc::classify);
    }

    @Test
    void assign_fromClassified_transitionsToAssigned() {
        Incident inc = newIncident();
        inc.classify();
        Technician tech = technician("T1");

        inc.assign(tech, T1);

        assertEquals(IncidentStatus.ASSIGNED, inc.getStatus());
        assertEquals(tech, inc.getAssignedTechnician().orElseThrow());
        assertEquals(T1, inc.getAssignedAt().orElseThrow());
    }

    @Test
    void assign_nullTechnician_throwsInvalidInput() {
        Incident inc = newIncident();
        inc.classify();
        assertThrows(InvalidInputException.class, () -> inc.assign(null, T1));
    }

    @Test
    void acknowledge_fromAssigned_transitionsToAcknowledged() {
        Incident inc = newIncident();
        inc.classify();
        inc.assign(technician("T1"), T1);
        inc.acknowledge(T2);

        assertEquals(IncidentStatus.ACKNOWLEDGED, inc.getStatus());
        assertEquals(T2, inc.getAcknowledgedAt().orElseThrow());
    }

    @Test
    void startWork_fromAcknowledged_transitionsToInProgress() {
        Incident inc = newIncident();
        inc.classify();
        inc.assign(technician("T1"), T1);
        inc.acknowledge(T2);
        inc.startWork();

        assertEquals(IncidentStatus.IN_PROGRESS, inc.getStatus());
    }

    // ===== Unassign: acknowledgedAt is preserved =====

    @Test
    void unassign_afterAcknowledge_acknowledgedAtRetained() {
        Incident inc = newIncident();
        inc.classify();
        inc.assign(technician("T1"), T1);
        inc.acknowledge(T2);
        inc.unassign();

        assertEquals(IncidentStatus.CLASSIFIED, inc.getStatus());
        // acknowledgedAt must be retained after unassign
        assertEquals(T2, inc.getAcknowledgedAt().orElseThrow());
        // assignee is cleared
        assertTrue(inc.getAssignedTechnician().isEmpty());
        assertTrue(inc.getAssignedAt().isEmpty());
    }

    @Test
    void acknowledge_secondCall_doesNotOverwriteAcknowledgedAt() {
        Incident inc = newIncident();
        inc.classify();
        inc.assign(technician("T1"), T1);
        inc.acknowledge(T2);
        // unassign and re-assign
        inc.unassign();
        inc.assign(technician("T2"), T3);
        // second acknowledge — must not overwrite acknowledgedAt
        inc.acknowledge(T4);

        assertEquals(T2, inc.getAcknowledgedAt().orElseThrow(),
                "First acknowledgement instant must be retained");
    }

    // ===== requestInfo / receiveInfo / pause accounting =====

    @Test
    void requestInfo_fromInProgress_transitionsToWaitingForUser() {
        Incident inc = progressedToInProgress();
        inc.requestInfo("T1", "Please provide logs", T3);

        assertEquals(IncidentStatus.WAITING_FOR_USER, inc.getStatus());
        assertEquals(T3, inc.getWaitingSince().orElseThrow());
        assertEquals(1, inc.getNotes().size());
        assertEquals(NoteType.INFO_REQUEST, inc.getNotes().get(0).type());
    }

    @Test
    void receiveInfo_fromWaitingForUser_transitionsToInProgressAndFoldsPause() {
        Incident inc = progressedToInProgress();
        inc.requestInfo("T1", "Please provide logs", T3);
        // T4 is 60 seconds after T3
        inc.receiveInfo("U1", "Here are the logs", T4);

        assertEquals(IncidentStatus.IN_PROGRESS, inc.getStatus());
        assertTrue(inc.getWaitingSince().isEmpty());
        assertEquals(Duration.ofSeconds(60), inc.getPausedDuration());
        assertEquals(2, inc.getNotes().size());
        assertEquals(NoteType.INFO_RESPONSE, inc.getNotes().get(1).type());
    }

    @Test
    void requestInfo_blankQuestion_throwsInvalidInput() {
        Incident inc = progressedToInProgress();
        assertThrows(InvalidInputException.class,
                () -> inc.requestInfo("T1", "", T3));
        assertThrows(InvalidInputException.class,
                () -> inc.requestInfo("T1", null, T3));
    }

    @Test
    void requestInfo_textTooLong_throwsInvalidInput() {
        Incident inc = progressedToInProgress();
        String longText = "A".repeat(4001);
        assertThrows(InvalidInputException.class,
                () -> inc.requestInfo("T1", longText, T3));
    }

    // ===== resolve / rejectResolution =====

    @Test
    void resolve_fromInProgress_transitionsToResolved() {
        Incident inc = progressedToInProgress();
        inc.resolve("T1", "Fixed the VPN config", T3);

        assertEquals(IncidentStatus.RESOLVED, inc.getStatus());
        assertTrue(inc.getResolution().isPresent());
        assertEquals("Fixed the VPN config", inc.getResolution().get().summary());
        assertEquals(T3, inc.getResolvedAt().orElseThrow());
    }

    @Test
    void rejectResolution_fromResolved_clearsResolutionAndReturnsToInProgress() {
        Incident inc = progressedToInProgress();
        inc.resolve("T1", "Fixed the VPN config", T3);
        inc.rejectResolution();

        assertEquals(IncidentStatus.IN_PROGRESS, inc.getStatus());
        assertTrue(inc.getResolution().isEmpty());
    }

    @Test
    void resolve_blankSummary_throwsInvalidInput() {
        Incident inc = progressedToInProgress();
        assertThrows(InvalidInputException.class,
                () -> inc.resolve("T1", "", T3));
        assertThrows(InvalidInputException.class,
                () -> inc.resolve("T1", null, T3));
    }

    // ===== verify / close =====

    @Test
    void verify_fromResolved_transitionsToVerified() {
        Incident inc = progressedToInProgress();
        inc.resolve("T1", "Fixed", T3);
        inc.verify(T4);

        assertEquals(IncidentStatus.VERIFIED, inc.getStatus());
        assertEquals(T4, inc.getVerifiedAt().orElseThrow());
    }

    @Test
    void close_fromVerified_transitionsToClosed() {
        Incident inc = progressedToInProgress();
        inc.resolve("T1", "Fixed", T3);
        inc.verify(T4);
        inc.close("ADMIN1", "Confirmed resolved", T5);

        assertEquals(IncidentStatus.CLOSED, inc.getStatus());
        assertEquals("ADMIN1", inc.getClosedById().orElseThrow());
        assertEquals("Confirmed resolved", inc.getClosureNote().orElseThrow());
        assertEquals(T5, inc.getClosedAt().orElseThrow());
    }

    @Test
    void close_blankClosureNote_storedAsNull() {
        Incident inc = progressedToInProgress();
        inc.resolve("T1", "Fixed", T3);
        inc.verify(T4);
        inc.close("ADMIN1", "   ", T5);

        assertTrue(inc.getClosureNote().isEmpty(),
                "Blank closureNote should be stored as null (empty Optional)");
    }

    @Test
    void close_nullClosureNote_storedAsNull() {
        Incident inc = progressedToInProgress();
        inc.resolve("T1", "Fixed", T3);
        inc.verify(T4);
        inc.close("ADMIN1", null, T5);

        assertTrue(inc.getClosureNote().isEmpty());
    }

    // ===== Notes =====

    @Test
    void addInvestigationNote_inAcknowledgedState_addsNote() {
        Incident inc = newIncident();
        inc.classify();
        inc.assign(technician("T1"), T1);
        inc.acknowledge(T2);

        inc.addInvestigationNote("T1", "Checked logs", T3);

        assertEquals(1, inc.getNotes().size());
        assertEquals(NoteType.INVESTIGATION, inc.getNotes().get(0).type());
    }

    @Test
    void addInvestigationNote_inNewState_throwsInvalidIncident() {
        Incident inc = newIncident();
        assertThrows(InvalidIncidentException.class,
                () -> inc.addInvestigationNote("T1", "note", T1));
    }

    @Test
    void addAdditionalInfo_inNewState_addsNote() {
        Incident inc = newIncident();
        inc.addAdditionalInfo("U1", "Extra context", T1);

        assertEquals(1, inc.getNotes().size());
        assertEquals(NoteType.ADDITIONAL_INFO, inc.getNotes().get(0).type());
    }

    @Test
    void addAdditionalInfo_inResolvedState_throwsInvalidIncident() {
        Incident inc = progressedToInProgress();
        inc.resolve("T1", "Fixed", T3);

        assertThrows(InvalidIncidentException.class,
                () -> inc.addAdditionalInfo("U1", "note", T4));
    }

    @Test
    void notes_returnedListIsUnmodifiable() {
        Incident inc = newIncident();
        List<IncidentNote> notes = inc.getNotes();
        assertThrows(UnsupportedOperationException.class,
                () -> notes.add(new IncidentNote("U1", com.iitsm.domain.model.NoteType.ADDITIONAL_INFO, "x", T1)));
    }

    // ===== Feedback =====

    @Test
    void submitFeedback_inResolvedState_setsRatingAndComment() {
        Incident inc = progressedToInProgress();
        inc.resolve("T1", "Fixed", T3);

        inc.submitFeedback("U1", 5, "Excellent service", T4);

        assertTrue(inc.getFeedback().isPresent());
        assertEquals(5, inc.getFeedback().get().rating());
        assertEquals("Excellent service", inc.getFeedback().get().comment());
    }

    @Test
    void submitFeedback_secondTime_throwsInvalidIncident() {
        Incident inc = progressedToInProgress();
        inc.resolve("T1", "Fixed", T3);
        inc.submitFeedback("U1", 4, "Good", T4);

        assertThrows(InvalidIncidentException.class,
                () -> inc.submitFeedback("U1", 3, "Changed mind", T5));
    }

    @Test
    void submitFeedback_inNewState_throwsInvalidIncident() {
        Incident inc = newIncident();
        assertThrows(InvalidIncidentException.class,
                () -> inc.submitFeedback("U1", 4, "Good", T1));
    }

    @Test
    void submitFeedback_invalidRating_throwsInvalidInput() {
        Incident inc = progressedToInProgress();
        inc.resolve("T1", "Fixed", T3);

        assertThrows(InvalidInputException.class,
                () -> inc.submitFeedback("U1", 0, "Bad", T4));
        assertThrows(InvalidInputException.class,
                () -> inc.submitFeedback("U1", 6, "Bad", T4));
    }

    @Test
    void submitFeedback_nullComment_accepted() {
        Incident inc = progressedToInProgress();
        inc.resolve("T1", "Fixed", T3);

        assertDoesNotThrow(() -> inc.submitFeedback("U1", 3, null, T4));
        assertEquals("", inc.getFeedback().get().comment(),
                "null comment should be normalised to empty string");
    }

    // ===== updatePriority =====

    @Test
    void updatePriority_inNewState_updatesPriorityFields() {
        Incident inc = newIncident();
        PriorityResult newPriority = new PriorityResult(64, PriorityLevel.P1);

        inc.updatePriority(Impact.CRITICAL, Urgency.CRITICAL, newPriority);

        assertEquals(Impact.CRITICAL, inc.getImpact());
        assertEquals(Urgency.CRITICAL, inc.getUrgency());
        assertEquals(PriorityLevel.P1, inc.getPriority());
        assertEquals(64, inc.getPriorityScore());
    }

    @Test
    void updatePriority_inResolvedState_throwsInvalidIncident() {
        Incident inc = progressedToInProgress();
        inc.resolve("T1", "Fixed", T3);

        assertThrows(InvalidIncidentException.class,
                () -> inc.updatePriority(Impact.CRITICAL, Urgency.CRITICAL,
                        new PriorityResult(64, PriorityLevel.P1)));
    }

    @Test
    void updatePriority_nullImpact_throwsInvalidInput() {
        Incident inc = newIncident();
        assertThrows(InvalidInputException.class,
                () -> inc.updatePriority(null, Urgency.LOW,
                        new PriorityResult(1, PriorityLevel.P4)));
    }

    @Test
    void updatePriority_nullResult_throwsInvalidInput() {
        Incident inc = newIncident();
        assertThrows(InvalidInputException.class,
                () -> inc.updatePriority(Impact.LOW, Urgency.LOW, null));
    }

    @Test
    void updatePriority_failedCallLeavesStateUnchanged() {
        Incident inc = progressedToInProgress();
        inc.resolve("T1", "Fixed", T3);
        // snapshot before the failing call
        PriorityLevel originalPriority = inc.getPriority();
        Impact originalImpact = inc.getImpact();

        assertThrows(InvalidIncidentException.class,
                () -> inc.updatePriority(Impact.CRITICAL, Urgency.CRITICAL,
                        new PriorityResult(64, PriorityLevel.P1)));

        // must be unchanged
        assertEquals(originalPriority, inc.getPriority());
        assertEquals(originalImpact, inc.getImpact());
    }

    // ===== ensureAssignable / ensureUnassignable (pure checks) =====

    @Test
    void ensureAssignable_fromClassified_doesNotThrow() {
        Incident inc = newIncident();
        inc.classify();
        assertDoesNotThrow(inc::ensureAssignable);
    }

    @Test
    void ensureAssignable_fromNew_throwsInvalidStateTransition() {
        Incident inc = newIncident();
        assertThrows(InvalidStateTransitionException.class, inc::ensureAssignable);
    }

    @Test
    void ensureUnassignable_fromAssigned_doesNotThrow() {
        Incident inc = newIncident();
        inc.classify();
        inc.assign(technician("T1"), T1);
        assertDoesNotThrow(inc::ensureUnassignable);
    }

    @Test
    void ensureUnassignable_fromNew_throwsInvalidStateTransition() {
        Incident inc = newIncident();
        assertThrows(InvalidStateTransitionException.class, inc::ensureUnassignable);
    }

    // ===== Multiple waiting periods / cumulative pause =====

    @Test
    void multipleWaitingPeriods_pauseDurationIsCumulative() {
        Incident inc = progressedToInProgress();
        // first wait: T3 → T4 (60s)
        inc.requestInfo("T1", "Question 1", T3);
        inc.receiveInfo("U1", "Answer 1", T4);
        // back to IN_PROGRESS; second wait: T4 → T5 (60s)
        inc.requestInfo("T1", "Question 2", T4);
        inc.receiveInfo("U1", "Answer 2", T5);

        assertEquals(Duration.ofSeconds(120), inc.getPausedDuration());
    }

    // ===== Invalid state transitions (selected high-value cases) =====

    @Test
    void close_fromResolved_throwsInvalidStateTransition() {
        // must pass through VERIFIED first
        Incident inc = progressedToInProgress();
        inc.resolve("T1", "Fixed", T3);

        assertThrows(InvalidStateTransitionException.class,
                () -> inc.close("ADMIN1", "note", T4));
    }

    @Test
    void resolve_fromClassified_throwsInvalidStateTransition() {
        Incident inc = newIncident();
        inc.classify();

        assertThrows(InvalidStateTransitionException.class,
                () -> inc.resolve("T1", "summary", T1));
    }

    @Test
    void anyAction_onClosedIncident_throwsInvalidStateTransition() {
        Incident inc = progressedToInProgress();
        inc.resolve("T1", "Fixed", T3);
        inc.verify(T4);
        inc.close("ADMIN1", null, T5);

        assertThrows(InvalidStateTransitionException.class, inc::classify);
        assertThrows(InvalidStateTransitionException.class, inc::ensureAssignable);
    }

    // ===== Unmodifiable collection getters =====

    @Test
    void getNotes_returnsUnmodifiableList() {
        Incident inc = newIncident();
        inc.addAdditionalInfo("U1", "info", T1);
        List<IncidentNote> notes = inc.getNotes();
        assertThrows(UnsupportedOperationException.class, () -> notes.add(null));
    }

    // ===== Helper to progress to IN_PROGRESS =====

    private Incident progressedToInProgress() {
        Incident inc = newIncident();
        inc.classify();
        inc.assign(technician("T1"), T1);
        inc.acknowledge(T2);
        inc.startWork();
        return inc;
    }
}

