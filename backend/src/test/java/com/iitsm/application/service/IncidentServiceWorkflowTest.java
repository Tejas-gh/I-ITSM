package com.iitsm.application.service;

import com.iitsm.application.command.CloseIncidentCommand;
import com.iitsm.application.command.CreateIncidentCommand;
import com.iitsm.application.command.IncidentTextCommand;
import com.iitsm.application.command.SubmitFeedbackCommand;
import com.iitsm.domain.event.DomainEvent;
import com.iitsm.domain.event.IncidentAcknowledged;
import com.iitsm.domain.event.IncidentClosed;
import com.iitsm.domain.event.IncidentResolved;
import com.iitsm.domain.event.IncidentStatusChanged;
import com.iitsm.domain.event.IncidentUpdated;
import com.iitsm.domain.exception.InvalidIncidentException;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.InvalidStateTransitionException;
import com.iitsm.domain.exception.UnauthorizedOperationException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.Urgency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IncidentServiceWorkflowTest {

    private ServiceFixture f;
    private Incident assignedIncident;

    @BeforeEach
    void setUp() {
        f = new ServiceFixture();

        // Create incident (auto-assigned to f.technician)
        assignedIncident = f.service.createIncident(new CreateIncidentCommand(
                f.employee.getId(), "Database Latency", "Queries are taking > 5s", f.itService.getId(),
                f.category.getId(), null, CategorySource.MANUAL, Impact.HIGH, Urgency.HIGH, 25
        ));
        f.listener.clear();
    }

    @Test
    @DisplayName("acknowledgeIncident moves state to ACKNOWLEDGED and publishes event")
    void acknowledgeIncident_happyPath_updatesStateAndPublishesEvent() {
        Incident acked = f.service.acknowledgeIncident(f.technician.getId(), assignedIncident.getId());

        assertEquals(IncidentStatus.ACKNOWLEDGED, acked.getStatus());
        assertTrue(acked.getAcknowledgedAt().isPresent());

        List<DomainEvent> events = f.listener.getEvents();
        assertEquals(1, events.size());
        assertInstanceOf(IncidentAcknowledged.class, events.get(0));
    }

    @Test
    @DisplayName("acknowledgeIncident by non-assignee throws UnauthorizedOperationException")
    void acknowledgeIncident_wrongActor_throwsUnauthorizedOperationException() {
        assertThrows(UnauthorizedOperationException.class,
                () -> f.service.acknowledgeIncident(f.technician2.getId(), assignedIncident.getId()));
    }

    @Test
    @DisplayName("startWork moves state to IN_PROGRESS and publishes WORK_STARTED event")
    void startWork_happyPath_updatesStateAndPublishesEvent() {
        f.service.acknowledgeIncident(f.technician.getId(), assignedIncident.getId());
        f.listener.clear();

        Incident started = f.service.startWork(f.technician.getId(), assignedIncident.getId());

        assertEquals(IncidentStatus.IN_PROGRESS, started.getStatus());
        List<DomainEvent> events = f.listener.getEvents();
        assertEquals(1, events.size());
        assertEquals("WORK_STARTED", events.get(0).action());
    }

    @Test
    @DisplayName("addInvestigationNote adds note and publishes NOTE_ADDED event")
    void addInvestigationNote_happyPath_addsNoteAndPublishesEvent() {
        f.service.acknowledgeIncident(f.technician.getId(), assignedIncident.getId());
        f.listener.clear();

        IncidentTextCommand cmd = new IncidentTextCommand(f.technician.getId(), assignedIncident.getId(), "Investigated DB thread pool");
        Incident updated = f.service.addInvestigationNote(cmd);

        assertEquals(1, updated.getNotes().size());
        assertEquals("Investigated DB thread pool", updated.getNotes().get(0).text());

        List<DomainEvent> events = f.listener.getEvents();
        assertEquals(1, events.size());
        assertInstanceOf(IncidentUpdated.class, events.get(0));
        assertEquals("NOTE_ADDED", events.get(0).action());
    }

    @Test
    @DisplayName("requestInfo and respondToInfoRequest transition between IN_PROGRESS and WAITING_FOR_USER")
    void requestAndRespondInfo_happyPath_transitionsAndPublishesEvents() {
        f.service.acknowledgeIncident(f.technician.getId(), assignedIncident.getId());
        f.service.startWork(f.technician.getId(), assignedIncident.getId());
        f.listener.clear();

        // 1. Request info (technician)
        Incident waiting = f.service.requestInfo(new IncidentTextCommand(f.technician.getId(), assignedIncident.getId(), "Which query is slow?"));
        assertEquals(IncidentStatus.WAITING_FOR_USER, waiting.getStatus());
        assertEquals("INFO_REQUESTED", f.listener.getEvents().get(0).action());

        // 2. Respond to info (employee)
        f.listener.clear();
        Incident resumed = f.service.respondToInfoRequest(new IncidentTextCommand(f.employee.getId(), assignedIncident.getId(), "The checkout query"));
        assertEquals(IncidentStatus.IN_PROGRESS, resumed.getStatus());
        assertEquals("INFO_RECEIVED", f.listener.getEvents().get(0).action());
    }

    @Test
    @DisplayName("addAdditionalInfo by reporter adds note and publishes ADDITIONAL_INFO_ADDED event")
    void addAdditionalInfo_happyPath_addsNoteAndPublishesEvent() {
        IncidentTextCommand cmd = new IncidentTextCommand(f.employee.getId(), assignedIncident.getId(), "Affects checkout flow primarily");
        Incident updated = f.service.addAdditionalInfo(cmd);

        assertEquals(1, updated.getNotes().size());
        assertEquals("ADDITIONAL_INFO_ADDED", f.listener.getEvents().get(0).action());
    }

    @Test
    @DisplayName("resolveIncident moves state to RESOLVED and publishes IncidentResolved event")
    void resolveIncident_happyPath_resolvesAndPublishesEvent() {
        f.service.acknowledgeIncident(f.technician.getId(), assignedIncident.getId());
        f.service.startWork(f.technician.getId(), assignedIncident.getId());
        f.listener.clear();

        IncidentTextCommand cmd = new IncidentTextCommand(f.technician.getId(), assignedIncident.getId(), "Scaled connection pool size");
        Incident resolved = f.service.resolveIncident(cmd);

        assertEquals(IncidentStatus.RESOLVED, resolved.getStatus());
        assertTrue(resolved.getResolution().isPresent());
        assertEquals("Scaled connection pool size", resolved.getResolution().get().summary());

        List<DomainEvent> events = f.listener.getEvents();
        assertEquals(1, events.size());
        assertInstanceOf(IncidentResolved.class, events.get(0));
    }

    @Test
    @DisplayName("verifyResolution moves state to VERIFIED and publishes VERIFIED event")
    void verifyResolution_happyPath_verifiesAndPublishesEvent() {
        f.service.acknowledgeIncident(f.technician.getId(), assignedIncident.getId());
        f.service.startWork(f.technician.getId(), assignedIncident.getId());
        f.service.resolveIncident(new IncidentTextCommand(f.technician.getId(), assignedIncident.getId(), "Fixed"));
        f.listener.clear();

        Incident verified = f.service.verifyResolution(f.employee.getId(), assignedIncident.getId());

        assertEquals(IncidentStatus.VERIFIED, verified.getStatus());
        assertTrue(verified.getVerifiedAt().isPresent());
        assertEquals("VERIFIED", f.listener.getEvents().get(0).action());
    }

    @Test
    @DisplayName("rejectResolution reopens incident from RESOLVED back to IN_PROGRESS")
    void rejectResolution_happyPath_reopensToInProgress() {
        f.service.acknowledgeIncident(f.technician.getId(), assignedIncident.getId());
        f.service.startWork(f.technician.getId(), assignedIncident.getId());
        f.service.resolveIncident(new IncidentTextCommand(f.technician.getId(), assignedIncident.getId(), "Fixed"));
        f.listener.clear();

        Incident reopened = f.service.rejectResolution(f.employee.getId(), assignedIncident.getId());

        assertEquals(IncidentStatus.IN_PROGRESS, reopened.getStatus());
        assertTrue(reopened.getResolution().isEmpty(), "Resolution should be cleared on rejection");
        assertEquals("RESOLUTION_REJECTED", f.listener.getEvents().get(0).action());
    }

    @Test
    @DisplayName("rejectResolution records a visible history note so employee and technician can see the rejection")
    void rejectResolution_recordsRejectionNoteInHistory() {
        f.service.acknowledgeIncident(f.technician.getId(), assignedIncident.getId());
        f.service.startWork(f.technician.getId(), assignedIncident.getId());
        f.service.resolveIncident(new IncidentTextCommand(f.technician.getId(), assignedIncident.getId(), "Fixed"));
        int notesBefore = f.service.getIncident(f.admin.getId(), assignedIncident.getId()).getNotes().size();

        Incident reopened = f.service.rejectResolution(f.employee.getId(), assignedIncident.getId());

        assertEquals(notesBefore + 1, reopened.getNotes().size());
        var note = reopened.getNotes().get(reopened.getNotes().size() - 1);
        assertEquals(f.employee.getId(), note.authorId());
        assertTrue(note.text().startsWith("RESOLUTION_REJECTED"));
        assertEquals(IncidentService.REJECTION_NOTE, note.text());
    }

    @Test
    @DisplayName("closeIncident by admin moves state to CLOSED and publishes IncidentClosed event")
    void closeIncident_happyPath_closesAndPublishesEvent() {
        f.service.acknowledgeIncident(f.technician.getId(), assignedIncident.getId());
        f.service.startWork(f.technician.getId(), assignedIncident.getId());
        f.service.resolveIncident(new IncidentTextCommand(f.technician.getId(), assignedIncident.getId(), "Fixed"));
        f.service.verifyResolution(f.employee.getId(), assignedIncident.getId());
        f.listener.clear();

        CloseIncidentCommand cmd = new CloseIncidentCommand(f.admin.getId(), assignedIncident.getId(), "Closing verified incident");
        Incident closed = f.service.closeIncident(cmd);

        assertEquals(IncidentStatus.CLOSED, closed.getStatus());
        assertTrue(closed.getClosedAt().isPresent());
        assertEquals("Closing verified incident", closed.getClosureNote().get());

        List<DomainEvent> events = f.listener.getEvents();
        assertEquals(1, events.size());
        assertInstanceOf(IncidentClosed.class, events.get(0));
    }

    @Test
    @DisplayName("submitFeedback records feedback and publishes FEEDBACK_SUBMITTED event")
    void submitFeedback_happyPath_recordsFeedbackAndPublishesEvent() {
        f.service.acknowledgeIncident(f.technician.getId(), assignedIncident.getId());
        f.service.startWork(f.technician.getId(), assignedIncident.getId());
        f.service.resolveIncident(new IncidentTextCommand(f.technician.getId(), assignedIncident.getId(), "Fixed"));
        f.listener.clear();

        SubmitFeedbackCommand cmd = new SubmitFeedbackCommand(f.employee.getId(), assignedIncident.getId(), 5, "Great support!");
        Incident feedbackAdded = f.service.submitFeedback(cmd);

        assertTrue(feedbackAdded.getFeedback().isPresent());
        assertEquals(5, feedbackAdded.getFeedback().get().rating());
        assertEquals("Great support!", feedbackAdded.getFeedback().get().comment());
        assertEquals("FEEDBACK_SUBMITTED", f.listener.getEvents().get(0).action());
    }

    @Test
    @DisplayName("submitFeedback twice throws InvalidIncidentException")
    void submitFeedback_twice_throwsInvalidIncidentException() {
        f.service.acknowledgeIncident(f.technician.getId(), assignedIncident.getId());
        f.service.startWork(f.technician.getId(), assignedIncident.getId());
        f.service.resolveIncident(new IncidentTextCommand(f.technician.getId(), assignedIncident.getId(), "Fixed"));

        f.service.submitFeedback(new SubmitFeedbackCommand(f.employee.getId(), assignedIncident.getId(), 5, "First feedback"));

        assertThrows(InvalidIncidentException.class,
                () -> f.service.submitFeedback(new SubmitFeedbackCommand(f.employee.getId(), assignedIncident.getId(), 4, "Second feedback")));
    }

    @Test
    @DisplayName("command with blank text throws InvalidInputException")
    void textCommands_blankText_throwsInvalidInputException() {
        f.service.acknowledgeIncident(f.technician.getId(), assignedIncident.getId());

        assertThrows(InvalidInputException.class,
                () -> f.service.addInvestigationNote(new IncidentTextCommand(f.technician.getId(), assignedIncident.getId(), "   ")));
    }

    @Test
    @DisplayName("workflow operation executed in wrong state throws InvalidStateTransitionException")
    void workflowOperations_wrongState_throwsInvalidStateTransitionException() {
        // assignedIncident is in ASSIGNED state, cannot directly start work without acknowledging
        assertThrows(InvalidStateTransitionException.class,
                () -> f.service.startWork(f.technician.getId(), assignedIncident.getId()));

        // cannot directly close from ASSIGNED state
        assertThrows(InvalidStateTransitionException.class,
                () -> f.service.closeIncident(new CloseIncidentCommand(f.admin.getId(), assignedIncident.getId(), "Premature close")));
    }
}
