package com.iitsm.domain.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.InvalidStateTransitionException;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.IncidentStatus;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.priority.PriorityResult;
import com.iitsm.support.TestData;

class IncidentRestoreTest {

    private static final Instant T0 = Instant.parse("2026-11-16T09:00:00Z");

    private final Technician tech = TestData.technician("T1", 3);

    private Incident progressed() {
        Incident inc = new Incident("INC1001",
                TestData.draft(TestData.employee("E1"), TestData.service("SVC1", BusinessCriticality.HIGH),
                        TestData.category("NETWORK"), Impact.HIGH, Urgency.HIGH, 5),
                new PriorityResult(18, PriorityLevel.P2), T0);
        inc.applySlaDeadlines(T0.plusSeconds(900), T0.plusSeconds(7200));
        inc.classify();
        inc.assign(tech, T0.plusSeconds(60));
        inc.acknowledge(T0.plusSeconds(120));
        inc.startWork();
        inc.addInvestigationNote("T1", "logs", T0.plusSeconds(180));
        inc.requestInfo("T1", "which host?", T0.plusSeconds(200));
        return inc;
    }

    private Incident restoreFrom(Incident s) {
        return Incident.restore(s.getId(), s.getTitle(), s.getDescription(), s.getReporter(), s.getService(),
                s.getCategory(), s.getSuggestedCategory().orElse(null), s.getCategorySource(), s.getImpact(),
                s.getUrgency(), s.getAffectedUsers(), s.getPriority(), s.getPriorityScore(), s.getCreatedAt(),
                s.getStatus(), s.getAssignedTechnician().orElse(null), s.getAssignedAt().orElse(null),
                s.getAcknowledgedAt().orElse(null), s.getVerifiedAt().orElse(null), s.getClosedAt().orElse(null),
                s.getClosedById().orElse(null), s.getClosureNote().orElse(null),
                s.getResponseDeadline().orElse(null), s.getResolutionDeadline().orElse(null),
                s.getWaitingSince().orElse(null), s.getPausedDuration(), s.getNotes(),
                s.getResolution().orElse(null), s.getFeedback().orElse(null));
    }

    @Test
    void restore_waitingIncident_reproducesAllStateAndStillFollowsTheStateMachine() {
        Incident source = progressed();
        Incident copy = restoreFrom(source);

        assertEquals(IncidentStatus.WAITING_FOR_USER, copy.getStatus());
        assertEquals(source.getWaitingSince(), copy.getWaitingSince());
        assertEquals(source.getNotes(), copy.getNotes());
        assertEquals(source.getAssignedTechnician(), copy.getAssignedTechnician());
        assertEquals(source.getResponseDeadline(), copy.getResponseDeadline());

        copy.receiveInfo("E1", "host-1", T0.plusSeconds(500));
        assertEquals(IncidentStatus.IN_PROGRESS, copy.getStatus());
        assertEquals(Duration.ofSeconds(300), copy.getPausedDuration());
        assertThrows(InvalidStateTransitionException.class, () -> copy.verify(T0.plusSeconds(600)));
    }

    @Test
    void restore_doesNotAliasTheNotesListPassedIn() {
        Incident source = progressed();
        List<IncidentNote> external = new java.util.ArrayList<>(source.getNotes());
        Incident copy = Incident.restore(source.getId(), source.getTitle(), source.getDescription(),
                source.getReporter(), source.getService(), source.getCategory(), null, CategorySource.MANUAL,
                source.getImpact(), source.getUrgency(), source.getAffectedUsers(), source.getPriority(),
                source.getPriorityScore(), source.getCreatedAt(), IncidentStatus.NEW, null, null, null, null, null,
                null, null, null, null, null, Duration.ZERO, external, null, null);
        external.clear();
        assertEquals(source.getNotes().size(), copy.getNotes().size());
    }

    @Test
    void restore_inactiveCategory_isAcceptedBecauseHistoryMayOutliveTheCategory() {
        Incident source = progressed();
        Category inactive = new Category("NETWORK", "Network");
        inactive.deactivate();
        Incident copy = Incident.restore(source.getId(), source.getTitle(), source.getDescription(),
                source.getReporter(), source.getService(), inactive, null, CategorySource.MANUAL,
                source.getImpact(), source.getUrgency(), 1, source.getPriority(), 1, source.getCreatedAt(),
                IncidentStatus.NEW, null, null, null, null, null, null, null, null, null, null, Duration.ZERO,
                List.of(), null, null);
        assertTrue(!copy.getCategory().isActive());
    }

    @Test
    void restore_invalidFields_areRejected() {
        Incident s = progressed();
        // non-MANUAL source without a matching suggestion
        assertThrows(InvalidInputException.class, () -> Incident.restore(s.getId(), s.getTitle(),
                s.getDescription(), s.getReporter(), s.getService(), s.getCategory(), null, CategorySource.KEYWORD,
                s.getImpact(), s.getUrgency(), 1, s.getPriority(), 1, s.getCreatedAt(), IncidentStatus.NEW,
                null, null, null, null, null, null, null, null, null, null, Duration.ZERO, List.of(), null, null));
        // only one deadline
        assertThrows(InvalidInputException.class, () -> Incident.restore(s.getId(), s.getTitle(),
                s.getDescription(), s.getReporter(), s.getService(), s.getCategory(), null, CategorySource.MANUAL,
                s.getImpact(), s.getUrgency(), 1, s.getPriority(), 1, s.getCreatedAt(), IncidentStatus.NEW,
                null, null, null, null, null, null, null, T0.plusSeconds(5), null, null, Duration.ZERO,
                List.of(), null, null));
        // negative paused duration, zero affected users, null status
        assertThrows(InvalidInputException.class, () -> Incident.restore(s.getId(), s.getTitle(),
                s.getDescription(), s.getReporter(), s.getService(), s.getCategory(), null, CategorySource.MANUAL,
                s.getImpact(), s.getUrgency(), 1, s.getPriority(), 1, s.getCreatedAt(), IncidentStatus.NEW,
                null, null, null, null, null, null, null, null, null, null, Duration.ofSeconds(-1),
                List.of(), null, null));
        assertThrows(InvalidInputException.class, () -> Incident.restore(s.getId(), s.getTitle(),
                s.getDescription(), s.getReporter(), s.getService(), s.getCategory(), null, CategorySource.MANUAL,
                s.getImpact(), s.getUrgency(), 0, s.getPriority(), 1, s.getCreatedAt(), IncidentStatus.NEW,
                null, null, null, null, null, null, null, null, null, null, Duration.ZERO, List.of(), null, null));
        assertThrows(InvalidInputException.class, () -> Incident.restore(s.getId(), s.getTitle(),
                s.getDescription(), s.getReporter(), s.getService(), s.getCategory(), null, CategorySource.MANUAL,
                s.getImpact(), s.getUrgency(), 1, s.getPriority(), 1, s.getCreatedAt(), null,
                null, null, null, null, null, null, null, null, null, null, Duration.ZERO, List.of(), null, null));
    }
}
