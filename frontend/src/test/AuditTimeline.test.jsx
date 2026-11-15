import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';

vi.mock('../api/apiClient', () => ({
  auditApi: { listForIncident: vi.fn() },
}));

import { auditApi } from '../api/apiClient';
import AuditTimeline from '../components/AuditTimeline';

const entries = [
  {
    occurredAt: '2026-11-16T09:10:00Z', actorId: 'TECH01', incidentId: 'INC1001',
    previousStatus: 'ASSIGNED', newStatus: 'ACKNOWLEDGED', action: 'INCIDENT_ACKNOWLEDGED',
    description: 'Technician acknowledged the incident',
  },
  {
    occurredAt: '2026-11-16T09:00:00Z', actorId: 'EMP01', incidentId: 'INC1001',
    previousStatus: null, newStatus: 'NEW', action: 'INCIDENT_CREATED',
    description: 'Incident created by reporter',
  },
];

describe('AuditTimeline', () => {
  beforeEach(() => vi.clearAllMocks());

  it('loads the audit history of the given incident from the backend', async () => {
    auditApi.listForIncident.mockResolvedValue(entries);
    render(<AuditTimeline incidentId="INC1001" />);

    await screen.findAllByTestId('audit-entry');
    expect(auditApi.listForIncident).toHaveBeenCalledWith('INC1001');
  });

  it('shows timestamp, actor, action, incident, previous/new state and description, oldest first', async () => {
    auditApi.listForIncident.mockResolvedValue(entries);
    render(<AuditTimeline incidentId="INC1001" />);

    const items = await screen.findAllByTestId('audit-entry');
    expect(items).toHaveLength(2);

    // Backend returned newest first; the timeline orders oldest first.
    const first = within(items[0]);
    expect(first.getByText('INCIDENT CREATED')).toBeInTheDocument();
    expect(first.getByText('EMP01')).toBeInTheDocument();
    expect(first.getByText(/on INC1001/)).toBeInTheDocument();
    expect(first.getByText('(none)')).toBeInTheDocument();
    expect(first.getByText('New')).toBeInTheDocument();
    expect(first.getByText('Incident created by reporter')).toBeInTheDocument();
    expect(first.getByText(new Date(entries[1].occurredAt).toLocaleString())).toBeInTheDocument();

    const second = within(items[1]);
    expect(second.getByText('INCIDENT ACKNOWLEDGED')).toBeInTheDocument();
    expect(second.getByText('Assigned')).toBeInTheDocument();
    expect(second.getByText('Acknowledged')).toBeInTheDocument();
  });

  it('shows an empty state when there are no audit entries', async () => {
    auditApi.listForIncident.mockResolvedValue([]);
    render(<AuditTimeline incidentId="INC1001" />);

    expect(await screen.findByText(/No audit entries recorded/)).toBeInTheDocument();
  });

  it('shows the backend error message when loading fails (e.g. 403)', async () => {
    auditApi.listForIncident.mockRejectedValue(new Error('Administrator access required'));
    render(<AuditTimeline incidentId="INC1001" />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Administrator access required');
  });

  it('reloads when the refresh key changes', async () => {
    auditApi.listForIncident.mockResolvedValue(entries);
    const { rerender } = render(<AuditTimeline incidentId="INC1001" refreshKey="ASSIGNED-0" />);
    await screen.findAllByTestId('audit-entry');

    rerender(<AuditTimeline incidentId="INC1001" refreshKey="ACKNOWLEDGED-0" />);
    await screen.findAllByTestId('audit-entry');

    expect(auditApi.listForIncident).toHaveBeenCalledTimes(2);
  });
});
