import { describe, it, expect } from 'vitest';
import { calculatePriorityScore, incidentApi } from '../api/apiClient';
import { IMPACT, URGENCY, PRIORITY_LEVEL, INCIDENT_STATUS } from '../constants/itsmConstants';

describe('Priority Calculation Engine (§K)', () => {
  it('calculates CRITICAL x CRITICAL x CRITICAL with 200 users as P1 (76 score)', () => {
    const { score, level } = calculatePriorityScore(IMPACT.CRITICAL, URGENCY.CRITICAL, 'CRITICAL', 200);
    expect(score).toBe(76);
    expect(level).toBe(PRIORITY_LEVEL.P1);
  });

  it('calculates LOW x LOW x LOW with 1 user as P4 (1 score)', () => {
    const { score, level } = calculatePriorityScore(IMPACT.LOW, URGENCY.LOW, 'LOW', 1);
    expect(score).toBe(1);
    expect(level).toBe(PRIORITY_LEVEL.P4);
  });

  it('calculates HIGH x HIGH x HIGH with 50 users as P2 (35 score)', () => {
    const { score, level } = calculatePriorityScore(IMPACT.HIGH, URGENCY.HIGH, 'HIGH', 50);
    expect(score).toBe(35);
    expect(level).toBe(PRIORITY_LEVEL.P2);
  });
});

describe('Incident API Mock Client', () => {
  it('lists visible incidents for role', async () => {
    const list = await incidentApi.listVisibleIncidents({ id: 'EMP01', role: 'EMPLOYEE' });
    expect(list.length).toBeGreaterThan(0);
    expect(list.every((i) => i.reporterId === 'EMP01')).toBe(true);
  });

  it('suggests category from text keyword classifier', async () => {
    const suggestion = await incidentApi.suggestCategory('VPN broken', 'cannot connect to tunnel');
    expect(suggestion).not.toBeNull();
    expect(suggestion.categoryId).toBe('NETWORK');
  });

  it('transitions state correctly on acknowledge and start work', async () => {
    const created = await incidentApi.createIncident(
      {
        title: 'Test Incident',
        description: 'Test Description',
        serviceId: 'SVC_NETWORK',
        categoryId: 'NETWORK',
        impact: IMPACT.LOW,
        urgency: URGENCY.LOW,
        affectedUsers: 1,
      },
      { id: 'EMP01', role: 'EMPLOYEE' }
    );

    const acked = await incidentApi.acknowledgeIncident(created.id);
    expect(acked.status).toBe(INCIDENT_STATUS.ACKNOWLEDGED);
    expect(acked.acknowledgedAt).not.toBeNull();

    const started = await incidentApi.startWork(created.id);
    expect(started.status).toBe(INCIDENT_STATUS.IN_PROGRESS);
  });
});
