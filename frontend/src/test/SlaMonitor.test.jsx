import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../api/apiClient', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    slaApi: {
      getViolations: vi.fn(),
      getAtRisk: vi.fn(),
      runMonitor: vi.fn(),
    },
    incidentApi: {
      listVisibleIncidents: vi.fn(),
    },
  };
});

const authValue = vi.hoisted(() => ({
  currentUser: { id: 'ADMIN01', name: 'Admin One', role: 'ADMINISTRATOR' },
  isAdmin: true,
}));

vi.mock('../context/AuthContext', () => ({
  useAuth: () => authValue,
}));

import { slaApi, incidentApi } from '../api/apiClient';
import SlaMonitorPage from '../pages/SlaMonitorPage';

describe('SlaMonitorPage', () => {
  const mockWarnings = [
    {
      id: 1,
      incidentId: 'INC-101',
      slaType: 'RESOLUTION',
      kind: 'WARNING',
      deadline: '2026-11-20T12:00:00Z',
      raisedAt: '2026-11-19T10:00:00Z',
    },
  ];

  const mockViolations = [
    {
      id: 2,
      incidentId: 'INC-202',
      slaType: 'RESPONSE',
      kind: 'VIOLATION',
      deadline: '2026-11-19T08:00:00Z',
      raisedAt: '2026-11-19T08:01:00Z',
    },
  ];

  const mockIncidents = [
    {
      id: 'INC-101',
      title: 'Database connection delay',
      priority: 'P2',
      status: 'ASSIGNED',
      responseDeadline: '2026-11-19T11:00:00Z',
      resolutionDeadline: '2026-11-20T12:00:00Z',
      acknowledgedAt: '2026-11-19T09:30:00Z',
      resolvedAt: null,
      slaPaused: false,
    },
    {
      id: 'INC-202',
      title: 'Authentication service 500 errors',
      priority: 'P1',
      status: 'LOGGED',
      responseDeadline: '2026-11-19T08:00:00Z',
      resolutionDeadline: '2026-11-19T10:00:00Z',
      acknowledgedAt: null,
      resolvedAt: null,
      slaPaused: false,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    slaApi.getAtRisk.mockResolvedValue(mockWarnings);
    slaApi.getViolations.mockResolvedValue(mockViolations);
    incidentApi.listVisibleIncidents.mockResolvedValue(mockIncidents);
  });

  function renderPage() {
    return render(
      <MemoryRouter>
        <SlaMonitorPage />
      </MemoryRouter>
    );
  }

  it('renders SLA metrics, at-risk warnings, and violations', async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/SLA Monitor & Compliance Center/i)).toBeInTheDocument();
    });

    // KPI counts
    expect(screen.getByText('INC-101')).toBeInTheDocument();

    // Default tab shows At-Risk Warnings
    expect(screen.getByText('Database connection delay')).toBeInTheDocument();
  });

  it('switches tabs to SLA Violations', async () => {
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/SLA Monitor & Compliance Center/i)).toBeInTheDocument();
    });

    const violationsTab = screen.getByRole('button', { name: /Violations/i });
    await user.click(violationsTab);

    expect(screen.getByText('INC-202')).toBeInTheDocument();
    expect(screen.getByText('Authentication service 500 errors')).toBeInTheDocument();
  });

  it('allows admin to trigger manual SLA monitor run', async () => {
    const user = userEvent.setup();
    slaApi.runMonitor.mockResolvedValue({
      incidentsEvaluated: 2,
      warningsGenerated: 1,
      violationsGenerated: 1,
      errors: 0,
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/Run SLA Monitor Now/i)).toBeInTheDocument();
    });

    const runBtn = screen.getByTestId('run-sla-monitor-btn');
    await user.click(runBtn);

    await waitFor(() => {
      expect(slaApi.runMonitor).toHaveBeenCalled();
      expect(screen.getByText(/Scanned 2 open incident\(s\)/i)).toBeInTheDocument();
    });
  });

  it('separates active violations from historical breaches and keeps both recorded', async () => {
    slaApi.getViolations.mockResolvedValue([
      ...mockViolations,
      { id: 3, incidentId: 'INC-303', slaType: 'RESOLUTION', kind: 'VIOLATION', deadline: '2026-11-18T08:00:00Z', raisedAt: '2026-11-18T08:01:00Z' },
    ]);
    incidentApi.listVisibleIncidents.mockResolvedValue([
      ...mockIncidents,
      { ...mockIncidents[0], id: 'INC-303', title: 'Printer outage', status: 'RESOLVED', resolvedAt: '2026-11-18T09:00:00Z' },
    ]);
    const user = userEvent.setup();
    renderPage();
    await screen.findByText(/SLA Monitor & Compliance Center/i);

    // KPI: 1 open incident in breach, 2 recorded in total
    expect(screen.getByText(/Open incidents in breach · 2 recorded in total/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Violations/i }));

    expect(screen.getByText('Active violations (1)')).toBeInTheDocument();
    expect(screen.getByText('Historical breaches (1)')).toBeInTheDocument();
    // The resolved incident is still listed (history), with its title
    expect(screen.getByText('INC-303')).toBeInTheDocument();
    expect(screen.getByText('Printer outage')).toBeInTheDocument();
  });

  it('offers "View Incident" links instead of a remediation action', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText(/SLA Monitor & Compliance Center/i);

    await user.click(screen.getByRole('button', { name: /Violations/i }));

    expect(screen.getByRole('link', { name: /View Incident/ })).toHaveAttribute('href', '/incidents/INC-202');
    expect(screen.queryByText(/Remediate/)).not.toBeInTheDocument();
  });

  it('shows a positive message when every recorded breach belongs to a resolved incident', async () => {
    incidentApi.listVisibleIncidents.mockResolvedValue([
      { ...mockIncidents[1], status: 'CLOSED', resolvedAt: '2026-11-19T09:00:00Z' },
    ]);
    const user = userEvent.setup();
    renderPage();
    await screen.findByText(/SLA Monitor & Compliance Center/i);

    await user.click(screen.getByRole('button', { name: /Violations/i }));

    expect(screen.getByText('Active violations (0)')).toBeInTheDocument();
    expect(screen.getByText(/No open incident is currently in breach/)).toBeInTheDocument();
    expect(screen.getByText('Historical breaches (1)')).toBeInTheDocument();
  });

  it('separates active at-risk incidents from historical warnings', async () => {
    slaApi.getAtRisk.mockResolvedValue([
      ...mockWarnings,
      { id: 9, incidentId: 'INC-404', slaType: 'RESOLUTION', kind: 'WARNING', deadline: '2026-11-18T12:00:00Z', raisedAt: '2026-11-18T10:00:00Z' },
    ]);
    incidentApi.listVisibleIncidents.mockResolvedValue([
      ...mockIncidents,
      { ...mockIncidents[0], id: 'INC-404', title: 'Old VPN issue', status: 'CLOSED', resolvedAt: '2026-11-18T11:00:00Z' },
    ]);
    renderPage();
    await screen.findByText(/SLA Monitor & Compliance Center/i);

    // KPI: 1 open incident at risk, 2 warnings recorded in total
    expect(screen.getByText(/Open incidents near their deadline · 2 recorded in total/)).toBeInTheDocument();
    // The At-Risk tab is the default one
    expect(screen.getByText('Active at-risk (1)')).toBeInTheDocument();
    expect(screen.getByText('Historical warnings (1)')).toBeInTheDocument();
    expect(screen.getByText('INC-404')).toBeInTheDocument();
    expect(screen.getByText('Old VPN issue')).toBeInTheDocument();
  });

  it('shows a positive message when every recorded warning belongs to a resolved incident', async () => {
    slaApi.getAtRisk.mockResolvedValue(mockWarnings);
    incidentApi.listVisibleIncidents.mockResolvedValue([
      { ...mockIncidents[0], status: 'RESOLVED', resolvedAt: '2026-11-19T10:00:00Z' },
    ]);
    renderPage();
    await screen.findByText(/SLA Monitor & Compliance Center/i);

    expect(screen.getByText('Active at-risk (0)')).toBeInTheDocument();
    expect(screen.getByText(/No open incident is currently at risk/)).toBeInTheDocument();
    expect(screen.getByText('Historical warnings (1)')).toBeInTheDocument();
  });
});
