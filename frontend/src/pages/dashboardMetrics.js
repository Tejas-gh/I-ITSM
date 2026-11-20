import { INCIDENT_STATUS } from '../constants/itsmConstants';

const { WAITING_FOR_USER, RESOLVED, VERIFIED, CLOSED } = INCIDENT_STATUS;

/** Open = not yet resolved, verified or closed. */
export const isOpen = (inc) => ![RESOLVED, VERIFIED, CLOSED].includes(inc.status);

/** Resolved from the user's point of view: resolved, verified or closed. */
export const isResolved = (inc) => !isOpen(inc);

/** True when the incident was ever waiting for the user (the resolution SLA clock was paused). */
const wasPaused = (inc) => (inc.notes || []).some((n) => n.type === 'INFO_REQUEST');

/**
 * Resolution target missed, judged from the incident's own data only.
 * The response exposes the base deadline but not the paused time, so incidents that were ever waiting for the user
 * are not counted: the dashboard never reports a miss it cannot be sure of.
 */
export function isResolutionMissed(inc, now = new Date()) {
  if (!inc.resolutionDeadline || wasPaused(inc)) return false;
  const deadline = new Date(inc.resolutionDeadline);
  if (inc.resolvedAt) return new Date(inc.resolvedAt) > deadline;
  if (inc.status === CLOSED || inc.status === WAITING_FOR_USER) return false;
  return now > deadline;
}

/** Personal figures for the employee dashboard (incidents are already scoped to the reporter by the backend). */
export function employeeMetrics(incidents, now = new Date()) {
  return {
    total: incidents.length,
    open: incidents.filter(isOpen).length,
    resolved: incidents.filter(isResolved).length,
    awaitingResponse: incidents.filter((i) => i.status === WAITING_FOR_USER).length,
    slaMissed: incidents.filter((i) => isResolutionMissed(i, now)).length,
  };
}

/** Keeps only the SLA alerts that belong to the given incidents (the SLA endpoints are not role-scoped). */
export function scopeAlertsToIncidents(alerts, incidents) {
  const ids = new Set(incidents.map((i) => i.id));
  return (alerts || []).filter((a) => ids.has(a.incidentId));
}
