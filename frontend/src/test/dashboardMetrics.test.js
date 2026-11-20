import { describe, it, expect } from 'vitest';
import {
  employeeMetrics, isOpen, isResolved, isResolutionMissed, scopeAlertsToIncidents,
} from '../pages/dashboardMetrics';

const NOW = new Date('2026-11-16T12:00:00Z');
const past = '2026-11-16T10:00:00Z';
const future = '2026-11-16T14:00:00Z';
const inc = (over) => ({ id: 'INC1', status: 'IN_PROGRESS', resolutionDeadline: future, resolvedAt: null, notes: [], ...over });

describe('isOpen / isResolved', () => {
  it('treats resolved, verified and closed as resolved and everything else as open', () => {
    ['NEW', 'CLASSIFIED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'WAITING_FOR_USER']
      .forEach((status) => expect(isOpen({ status })).toBe(true));
    ['RESOLVED', 'VERIFIED', 'CLOSED'].forEach((status) => expect(isResolved({ status })).toBe(true));
  });
});

describe('isResolutionMissed', () => {
  it('flags an unresolved incident past its resolution deadline', () => {
    expect(isResolutionMissed(inc({ resolutionDeadline: past }), NOW)).toBe(true);
  });

  it('does not flag an unresolved incident still within its deadline', () => {
    expect(isResolutionMissed(inc({ resolutionDeadline: future }), NOW)).toBe(false);
  });

  it('flags an incident resolved after its deadline but not one resolved in time', () => {
    expect(isResolutionMissed(inc({ status: 'RESOLVED', resolutionDeadline: past, resolvedAt: future }), NOW)).toBe(true);
    expect(isResolutionMissed(inc({ status: 'RESOLVED', resolutionDeadline: future, resolvedAt: past }), NOW)).toBe(false);
  });

  it('does not count an incident that is waiting for the user (SLA clock paused)', () => {
    expect(isResolutionMissed(inc({ status: 'WAITING_FOR_USER', resolutionDeadline: past }), NOW)).toBe(false);
  });

  it('does not count incidents that were ever paused, because the paused time is not exposed', () => {
    const paused = [{ type: 'INFO_REQUEST', text: 'Which VPN?' }];
    expect(isResolutionMissed(inc({ resolutionDeadline: past, notes: paused }), NOW)).toBe(false);
    expect(isResolutionMissed(inc({ status: 'RESOLVED', resolutionDeadline: past, resolvedAt: future, notes: paused }), NOW)).toBe(false);
  });

  it('ignores incidents without a deadline and closed incidents without a resolution time', () => {
    expect(isResolutionMissed(inc({ resolutionDeadline: null }), NOW)).toBe(false);
    expect(isResolutionMissed(inc({ status: 'CLOSED', resolutionDeadline: past }), NOW)).toBe(false);
  });
});

describe('employeeMetrics', () => {
  it('summarises the personal incidents', () => {
    const list = [
      inc({ id: 'A', status: 'IN_PROGRESS', resolutionDeadline: past }),
      inc({ id: 'B', status: 'WAITING_FOR_USER' }),
      inc({ id: 'C', status: 'RESOLVED', resolvedAt: past, resolutionDeadline: future }),
      inc({ id: 'D', status: 'CLOSED', resolvedAt: past, resolutionDeadline: future }),
    ];

    expect(employeeMetrics(list, NOW)).toEqual({
      total: 4, open: 2, resolved: 2, awaitingResponse: 1, slaMissed: 1,
    });
  });

  it('returns zeros for no incidents', () => {
    expect(employeeMetrics([], NOW)).toEqual({ total: 0, open: 0, resolved: 0, awaitingResponse: 0, slaMissed: 0 });
  });
});

describe('scopeAlertsToIncidents', () => {
  it('keeps only alerts of the given incidents and tolerates missing input', () => {
    const alerts = [{ incidentId: 'A' }, { incidentId: 'X' }, { incidentId: 'B' }];

    expect(scopeAlertsToIncidents(alerts, [{ id: 'A' }, { id: 'B' }])).toEqual([{ incidentId: 'A' }, { incidentId: 'B' }]);
    expect(scopeAlertsToIncidents(null, [{ id: 'A' }])).toEqual([]);
    expect(scopeAlertsToIncidents(alerts, [])).toEqual([]);
  });
});
