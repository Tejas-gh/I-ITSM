package com.iitsm.domain.sla;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.MissingSlaException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentDraft;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.priority.PriorityResult;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SlaEvaluatorTest {

    private final SlaEvaluator evaluator = new SlaEvaluator();
    private final Instant createdAt = Instant.parse("2026-11-16T09:00:00Z");
    private final Instant responseDeadline = createdAt.plus(Duration.ofMinutes(10)); // target 10 min, 20% = 2 min
    private final Instant resolutionDeadline = createdAt.plus(Duration.ofMinutes(30)); // target 30 min, 20% = 6 min

    private Incident incident;
    private Technician technician;

    @BeforeEach
    void setUp() {
        IncidentDraft draft = TestData.draft(
                TestData.employee("EMP01"),
                TestData.service("SRV-01", BusinessCriticality.HIGH),
                TestData.category("APPLICATION"),
                Impact.HIGH,
                Urgency.HIGH,
                10
        );
        incident = new Incident("INC1001", draft, new PriorityResult(36, PriorityLevel.P1), createdAt);
        incident.applySlaDeadlines(responseDeadline, resolutionDeadline);

        technician = TestData.technician("TECH01", 5);
        incident.classify();
        incident.assign(technician, createdAt.plusSeconds(30));
    }

    @Test
    @DisplayName("evaluate returns ON_TRACK when time is before warning threshold")
    void evaluate_beforeWarningThreshold_returnsOnTrack() {
        Instant evalTime = createdAt.plus(Duration.ofMinutes(5));
        SlaEvaluation result = evaluator.evaluate(incident, evalTime);

        assertEquals(SlaStatus.ON_TRACK, result.response());
        assertEquals(SlaStatus.ON_TRACK, result.resolution());
        assertEquals(SlaStatus.ON_TRACK, result.overall());
    }

    @Test
    @DisplayName("evaluate returns WARNING at exactly deadline - target/5")
    void evaluate_exactlyAtWarningThreshold_returnsWarning() {
        // Response target = 10m, warning threshold = 10m - 2m = 8m (09:08:00Z)
        Instant warningTime = createdAt.plus(Duration.ofMinutes(8));
        SlaEvaluation result = evaluator.evaluate(incident, warningTime);

        assertEquals(SlaStatus.WARNING, result.response());
        assertEquals(SlaStatus.ON_TRACK, result.resolution());
        assertEquals(SlaStatus.WARNING, result.overall());
    }

    @Test
    @DisplayName("evaluate returns ON_TRACK 1 millisecond before warning threshold")
    void evaluate_oneMillisecondBeforeWarningThreshold_returnsOnTrack() {
        Instant justBeforeWarning = createdAt.plus(Duration.ofMinutes(8)).minusMillis(1);
        SlaEvaluation result = evaluator.evaluate(incident, justBeforeWarning);

        assertEquals(SlaStatus.ON_TRACK, result.response());
    }

    @Test
    @DisplayName("evaluate at exactly deadline is not breached (returns WARNING)")
    void evaluate_exactlyAtDeadline_notBreached() {
        SlaEvaluation result = evaluator.evaluate(incident, responseDeadline);

        assertEquals(SlaStatus.WARNING, result.response());
        assertNotEquals(SlaStatus.BREACHED, result.response());
    }

    @Test
    @DisplayName("evaluate 1 millisecond after deadline returns BREACHED")
    void evaluate_oneMillisecondAfterDeadline_returnsBreached() {
        Instant breachedTime = responseDeadline.plusMillis(1);
        SlaEvaluation result = evaluator.evaluate(incident, breachedTime);

        assertEquals(SlaStatus.BREACHED, result.response());
        assertEquals(SlaStatus.BREACHED, result.overall());
    }

    @Test
    @DisplayName("evaluate acknowledges on time marks response as MET")
    void evaluate_acknowledgedOnTime_responseIsMet() {
        incident.acknowledge(createdAt.plus(Duration.ofMinutes(5)));

        SlaEvaluation result = evaluator.evaluate(incident, createdAt.plus(Duration.ofMinutes(15)));
        assertEquals(SlaStatus.MET, result.response());
    }

    @Test
    @DisplayName("evaluate acknowledges exactly at deadline marks response as MET")
    void evaluate_acknowledgedExactlyAtDeadline_responseIsMet() {
        incident.acknowledge(responseDeadline);

        SlaEvaluation result = evaluator.evaluate(incident, responseDeadline.plus(Duration.ofMinutes(5)));
        assertEquals(SlaStatus.MET, result.response());
    }

    @Test
    @DisplayName("evaluate acknowledges after deadline marks response as BREACHED")
    void evaluate_acknowledgedAfterDeadline_responseIsBreached() {
        incident.acknowledge(responseDeadline.plusMillis(1));

        SlaEvaluation result = evaluator.evaluate(incident, responseDeadline.plus(Duration.ofMinutes(5)));
        assertEquals(SlaStatus.BREACHED, result.response());
    }

    @Test
    @DisplayName("evaluate resolves on time marks resolution as MET and both MET produces overall MET")
    void evaluate_resolvedOnTime_resolutionAndOverallAreMet() {
        incident.acknowledge(createdAt.plus(Duration.ofMinutes(5)));
        incident.startWork();
        incident.resolve("TECH01", "Fixed issue", createdAt.plus(Duration.ofMinutes(25)));

        SlaEvaluation result = evaluator.evaluate(incident, createdAt.plus(Duration.ofMinutes(28)));
        assertEquals(SlaStatus.MET, result.response());
        assertEquals(SlaStatus.MET, result.resolution());
        assertEquals(SlaStatus.MET, result.overall());
    }

    @Test
    @DisplayName("active wait pause extends effective resolution deadline while in WAITING_FOR_USER")
    void evaluate_waitingForUserActivePause_extendsEffectiveResolutionDeadline() {
        incident.acknowledge(createdAt.plus(Duration.ofMinutes(5)));
        incident.startWork();
        // Request info at 09:20:00Z
        Instant waitStart = createdAt.plus(Duration.ofMinutes(20));
        incident.requestInfo("TECH01", "Need info", waitStart);

        // At 09:35:00Z (which is 5 min past original resolution deadline 09:30:00Z):
        // active pause is 15 min, so effective resolution deadline is 09:45:00Z
        Instant now = createdAt.plus(Duration.ofMinutes(35));
        SlaEvaluation result = evaluator.evaluate(incident, now);

        assertEquals(resolutionDeadline.plus(Duration.ofMinutes(15)), result.effectiveResolutionDeadline());
        assertNotEquals(SlaStatus.BREACHED, result.resolution());
    }

    @Test
    @DisplayName("pause is folded into pausedDuration after receiveInfo and effective deadline is preserved")
    void evaluate_pauseFoldedAfterReceiveInfo_preservesExtendedResolutionDeadline() {
        incident.acknowledge(createdAt.plus(Duration.ofMinutes(5)));
        incident.startWork();
        Instant waitStart = createdAt.plus(Duration.ofMinutes(20));
        incident.requestInfo("TECH01", "Need info", waitStart);
        Instant waitEnd = createdAt.plus(Duration.ofMinutes(35));
        incident.receiveInfo("EMP01", "Here is info", waitEnd);

        // Effective resolution deadline should now be 09:45:00Z even though waitingSince is null
        Instant now = createdAt.plus(Duration.ofMinutes(38));
        SlaEvaluation result = evaluator.evaluate(incident, now);

        assertEquals(resolutionDeadline.plus(Duration.ofMinutes(15)), result.effectiveResolutionDeadline());
        assertEquals(SlaStatus.ON_TRACK, result.resolution());
    }

    @Test
    @DisplayName("rejectResolution clears resolvedAt and causes resolution SLA to be evaluated again against now")
    void evaluate_rejectResolution_clearsResolvedAndReevaluates() {
        incident.acknowledge(createdAt.plus(Duration.ofMinutes(5)));
        incident.startWork();
        incident.resolve("TECH01", "Initial fix", createdAt.plus(Duration.ofMinutes(20)));

        SlaEvaluation beforeReject = evaluator.evaluate(incident, createdAt.plus(Duration.ofMinutes(25)));
        assertEquals(SlaStatus.MET, beforeReject.resolution());

        incident.rejectResolution();

        // At 09:31:00Z (past resolution deadline): should be BREACHED because resolution is no longer MET
        SlaEvaluation afterReject = evaluator.evaluate(incident, resolutionDeadline.plus(Duration.ofMinutes(1)));
        assertEquals(SlaStatus.BREACHED, afterReject.resolution());
    }

    @Test
    @DisplayName("overall precedence rules: BREACHED > WARNING > MET > ON_TRACK")
    void evaluate_overallPrecedence_followsContract() {
        // 1. One MET and one WARNING -> overall WARNING
        incident.acknowledge(createdAt.plus(Duration.ofMinutes(5))); // response MET
        Instant warningTime = resolutionDeadline.minus(Duration.ofMinutes(3)); // resolution WARNING
        SlaEvaluation evalWarning = evaluator.evaluate(incident, warningTime);
        assertEquals(SlaStatus.MET, evalWarning.response());
        assertEquals(SlaStatus.WARNING, evalWarning.resolution());
        assertEquals(SlaStatus.WARNING, evalWarning.overall());

        // 2. One MET and one BREACHED -> overall BREACHED
        Instant breachedTime = resolutionDeadline.plusSeconds(10);
        SlaEvaluation evalBreached = evaluator.evaluate(incident, breachedTime);
        assertEquals(SlaStatus.MET, evalBreached.response());
        assertEquals(SlaStatus.BREACHED, evalBreached.resolution());
        assertEquals(SlaStatus.BREACHED, evalBreached.overall());
    }

    @Test
    @DisplayName("incident without SLA deadlines throws MissingSlaException")
    void evaluate_incidentWithoutDeadlines_throwsMissingSlaException() {
        IncidentDraft draft = TestData.draft(
                TestData.employee("EMP01"),
                TestData.service("SRV-01", BusinessCriticality.HIGH),
                TestData.category("APPLICATION"),
                Impact.HIGH,
                Urgency.HIGH,
                10
        );
        Incident noDeadlines = new Incident("INC9999", draft, new PriorityResult(36, PriorityLevel.P1), createdAt);

        MissingSlaException ex = assertThrows(
                MissingSlaException.class,
                () -> evaluator.evaluate(noDeadlines, createdAt)
        );
        assertEquals("MISSING_SLA", ex.getCode());
        assertTrue(ex.getMessage().contains("INC9999"));
    }

    @Test
    @DisplayName("evaluate validates null arguments")
    void evaluate_nullArguments_throwsInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> evaluator.evaluate(null, createdAt));
        assertThrows(InvalidInputException.class, () -> evaluator.evaluate(incident, null));
    }
}
