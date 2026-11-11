package com.iitsm.domain.sla;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.MissingSlaException;
import com.iitsm.domain.incident.Incident;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Pure evaluator of SLA breach status and deadlines against an incident and evaluation timestamp.
 */
public final class SlaEvaluator {

    public SlaEvaluator() {
    }

    public SlaEvaluation evaluate(Incident incident, Instant now) {
        if (incident == null) {
            throw new InvalidInputException("incident must not be null");
        }
        if (now == null) {
            throw new InvalidInputException("now must not be null");
        }

        Optional<Instant> optResponseDeadline = incident.getResponseDeadline();
        Optional<Instant> optResolutionDeadline = incident.getResolutionDeadline();

        if (optResponseDeadline.isEmpty() || optResolutionDeadline.isEmpty()) {
            throw new MissingSlaException("Incident " + incident.getId() + " has no SLA deadlines");
        }

        Instant createdAt = incident.getCreatedAt();
        Instant responseDeadline = optResponseDeadline.get();
        Instant resolutionDeadline = optResolutionDeadline.get();
        Duration pausedDuration = incident.getPausedDuration();
        Optional<Instant> waitingSince = incident.getWaitingSince();

        Instant effectiveResponseDeadline = responseDeadline;

        Duration currentWait = Duration.ZERO;
        if (waitingSince.isPresent()) {
            Duration diff = Duration.between(waitingSince.get(), now);
            if (!diff.isNegative()) {
                currentWait = diff;
            }
        }
        Instant effectiveResolutionDeadline = resolutionDeadline.plus(pausedDuration).plus(currentWait);

        Duration responseTarget = Duration.between(createdAt, responseDeadline);
        Duration resolutionTarget = Duration.between(createdAt, resolutionDeadline);

        SlaStatus responseStatus = evaluateTarget(
                incident.getAcknowledgedAt(),
                now,
                effectiveResponseDeadline,
                responseTarget
        );

        SlaStatus resolutionStatus = evaluateTarget(
                incident.getResolvedAt(),
                now,
                effectiveResolutionDeadline,
                resolutionTarget
        );

        SlaStatus overall = determineOverall(responseStatus, resolutionStatus);

        return new SlaEvaluation(
                responseStatus,
                resolutionStatus,
                overall,
                effectiveResponseDeadline,
                effectiveResolutionDeadline
        );
    }

    private SlaStatus evaluateTarget(
            Optional<Instant> doneAt,
            Instant now,
            Instant effectiveDeadline,
            Duration baseTarget
    ) {
        if (doneAt.isPresent()) {
            return !doneAt.get().isAfter(effectiveDeadline) ? SlaStatus.MET : SlaStatus.BREACHED;
        }

        if (now.isAfter(effectiveDeadline)) {
            return SlaStatus.BREACHED;
        }

        Instant warningThreshold = effectiveDeadline.minus(baseTarget.dividedBy(5));
        if (!now.isBefore(warningThreshold)) {
            return SlaStatus.WARNING;
        }

        return SlaStatus.ON_TRACK;
    }

    private SlaStatus determineOverall(SlaStatus response, SlaStatus resolution) {
        if (response == SlaStatus.BREACHED || resolution == SlaStatus.BREACHED) {
            return SlaStatus.BREACHED;
        }
        if (response == SlaStatus.WARNING || resolution == SlaStatus.WARNING) {
            return SlaStatus.WARNING;
        }
        if (response == SlaStatus.MET && resolution == SlaStatus.MET) {
            return SlaStatus.MET;
        }
        return SlaStatus.ON_TRACK;
    }
}
