import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

const auth = vi.hoisted(() => ({ value: {} }));
vi.mock('../context/AuthContext', () => ({ useAuth: () => auth.value }));
vi.mock('../api/apiClient', () => ({
  incidentApi: { listVisibleIncidents: vi.fn() },
  referenceApi: { listServices: vi.fn() },
  slaApi: { getAtRisk: vi.fn(), getViolations: vi.fn() },
  dashboardApi: { getTechnicianWorkloads: vi.fn() },
}));

import { incidentApi, referenceApi, slaApi, dashboardApi } from '../api/apiClient';
import DashboardPage from '../pages/DashboardPage';

const FUTURE = '2099-01-01T00:00:00Z';
const PAST = '2020-01-01T00:00:00Z';
const incident = (over) => ({
  id: 'INC1', title: 'Title', priority: 'P3', status: 'IN_PROGRESS', serviceId: 'SVC1',
  resolutionDeadline: FUTURE, resolvedAt: null, notes: [], ...over,
});

const user = (role) => ({ id: 'U1', name: 'User One', role });
const asRole = (role) => ({
  currentUser: user(role),
  isEmployee: role === 'EMPLOYEE',
  isAdmin: role === 'ADMINISTRATOR' || role === 'IT_MANAGER',
  isTechnician: role === 'TECHNICIAN' || role === 'TEAM_LEAD',
});

// Metric labels are <div>s; this keeps e.g. "Resolved" from also matching a status badge <span>
const metric = (label) => screen.getByText(label, { selector: 'div' }).parentElement;
const renderPage = () => render(<MemoryRouter><DashboardPage /></MemoryRouter>);

beforeEach(() => {
  vi.clearAllMocks();
  referenceApi.listServices.mockResolvedValue([{ id: 'SVC1', name: 'VPN' }]);
  slaApi.getAtRisk.mockResolvedValue([]);
  slaApi.getViolations.mockResolvedValue([]);
  dashboardApi.getTechnicianWorkloads.mockResolvedValue([]);
});

describe('Employee dashboard', () => {
  beforeEach(() => {
    auth.value = asRole('EMPLOYEE');
    incidentApi.listVisibleIncidents.mockResolvedValue([
      incident({ id: 'A', status: 'IN_PROGRESS', resolutionDeadline: PAST }), // missed
      incident({ id: 'B', status: 'WAITING_FOR_USER' }),
      incident({ id: 'C', status: 'RESOLVED', resolvedAt: '2026-01-01T00:00:00Z' }),
      incident({ id: 'D', status: 'CLOSED', resolvedAt: '2026-01-01T00:00:00Z' }),
    ]);
  });

  it('shows personal metrics with the right values', async () => {
    renderPage();

    await screen.findByText('My Incidents');
    expect(within(metric('My Incidents')).getByText('4')).toBeInTheDocument();
    expect(within(metric('My Open Incidents')).getByText('2')).toBeInTheDocument();
    expect(within(metric('My Resolved Incidents')).getByText('2')).toBeInTheDocument();
    expect(within(metric('Awaiting My Response')).getByText('1')).toBeInTheDocument();
    expect(within(metric('My SLA Missed')).getByText('1')).toBeInTheDocument();
    expect(screen.getByText('My Support Overview')).toBeInTheDocument();
    expect(screen.getByText('My Recent Incidents')).toBeInTheDocument();
  });

  it('no longer shows the support-oriented tiles or the service distribution', async () => {
    renderPage();
    await screen.findByText('My Incidents');

    ['Total Incidents', 'P1 Critical', '⚠️ SLA At-Risk', '🚨 SLA Violations', '📈 SLA Compliance',
      'Service Incident Distribution', 'Technician Workload', 'Awaiting Acknowledgement']
      .forEach((label) => expect(screen.queryByText(label)).not.toBeInTheDocument());
  });

  it('does not call the staff-only SLA or dashboard endpoints', async () => {
    renderPage();
    await screen.findByText('My Incidents');

    expect(slaApi.getAtRisk).not.toHaveBeenCalled();
    expect(slaApi.getViolations).not.toHaveBeenCalled();
    expect(dashboardApi.getTechnicianWorkloads).not.toHaveBeenCalled();
  });
});

describe('Technician dashboard', () => {
  beforeEach(() => {
    auth.value = asRole('TECHNICIAN');
    incidentApi.listVisibleIncidents.mockResolvedValue([
      incident({ id: 'A', status: 'ASSIGNED' }),
      incident({ id: 'B', status: 'ASSIGNED' }),
      incident({ id: 'C', status: 'IN_PROGRESS' }),
    ]);
    // The SLA endpoints return alerts for the whole system
    slaApi.getAtRisk.mockResolvedValue([{ incidentId: 'A' }, { incidentId: 'OTHER1' }, { incidentId: 'OTHER2' }]);
    slaApi.getViolations.mockResolvedValue([{ incidentId: 'C' }, { incidentId: 'OTHER3' }, { incidentId: 'OTHER4' }, { incidentId: 'OTHER5' }]);
  });

  it('counts only SLA alerts of the technician\'s own incidents', async () => {
    renderPage();

    await screen.findByText('⚠️ SLA At-Risk');
    expect(within(metric('⚠️ SLA At-Risk')).getByText('1')).toBeInTheDocument();
    expect(within(metric('🚨 SLA Violations')).getByText('1')).toBeInTheDocument();
    // 1 of 3 own incidents violated -> 67 %, not clamped to 0 % by system-wide alerts
    expect(within(metric('📈 SLA Compliance')).getByText('67%')).toBeInTheDocument();
  });

  it('shows how many incidents await acknowledgement', async () => {
    renderPage();

    await screen.findByText('Awaiting Acknowledgement');
    expect(within(metric('Awaiting Acknowledgement')).getByText('2')).toBeInTheDocument();
  });

  it('keeps the SLA tiles clickable and shows no workload panel or admin-only card', async () => {
    renderPage();
    await screen.findByText('⚠️ SLA At-Risk');

    expect(metric('⚠️ SLA At-Risk').closest('a')).toHaveAttribute('href', '/sla');
    expect(screen.queryByTestId('technician-workload')).not.toBeInTheDocument();
    expect(screen.queryByText('Resolved', { selector: 'div' })).not.toBeInTheDocument();
    expect(dashboardApi.getTechnicianWorkloads).not.toHaveBeenCalled();
  });
});

describe('Administrator dashboard', () => {
  beforeEach(() => {
    auth.value = asRole('ADMINISTRATOR');
    incidentApi.listVisibleIncidents.mockResolvedValue([
      incident({ id: 'A', status: 'ASSIGNED' }),
      incident({ id: 'B', status: 'RESOLVED', resolvedAt: '2026-01-01T00:00:00Z' }),
      incident({ id: 'C', status: 'CLOSED', resolvedAt: '2026-01-01T00:00:00Z' }),
    ]);
    slaApi.getViolations.mockResolvedValue([{ incidentId: 'A' }, { incidentId: 'Z' }]);
  });

  it('shows the resolved count and uses all SLA alerts system-wide', async () => {
    renderPage();

    await screen.findByText('Resolved', { selector: 'div' });
    expect(within(metric('Resolved')).getByText('2')).toBeInTheDocument();
    // 2 violations are recorded (A, Z) but only A belongs to an open incident
    expect(within(metric('🚨 SLA Violations')).getByText('1')).toBeInTheDocument();
    expect(screen.getByText(/Open incidents in breach · 2 recorded/)).toBeInTheDocument();
    expect(screen.queryByText('Awaiting Acknowledgement')).not.toBeInTheDocument();
  });

  it('keeps a breach on a resolved incident in the compliance rate but not in the active violations', async () => {
    incidentApi.listVisibleIncidents.mockResolvedValue([
      incident({ id: 'A', status: 'IN_PROGRESS' }),
      incident({ id: 'B', status: 'RESOLVED', resolvedAt: '2026-01-01T00:00:00Z' }),
    ]);
    slaApi.getViolations.mockResolvedValue([{ incidentId: 'B' }]); // recorded, but B is resolved now
    renderPage();

    await screen.findByText('🚨 SLA Violations');
    expect(within(metric('🚨 SLA Violations')).getByText('0')).toBeInTheDocument();
    expect(screen.getByText(/Open incidents in breach · 1 recorded/)).toBeInTheDocument();
    expect(within(metric('📈 SLA Compliance')).getByText('50%')).toBeInTheDocument(); // 1 of 2 breached
    expect(screen.getByText('All SLAs are healthy')).toBeInTheDocument();
  });

  it('counts at-risk only for open incidents; a warning on a resolved incident is history', async () => {
    incidentApi.listVisibleIncidents.mockResolvedValue([
      incident({ id: 'A', status: 'IN_PROGRESS' }),
      incident({ id: 'B', status: 'RESOLVED', resolvedAt: '2026-01-01T00:00:00Z' }),
    ]);
    slaApi.getViolations.mockResolvedValue([]); // isolate the at-risk behaviour from the admin default violations
    slaApi.getAtRisk.mockResolvedValue([{ incidentId: 'B' }]); // recorded, but B is resolved now
    renderPage();

    await screen.findByText('⚠️ SLA At-Risk');
    expect(within(metric('⚠️ SLA At-Risk')).getByText('0')).toBeInTheDocument();
    expect(screen.getByText('All SLAs are healthy')).toBeInTheDocument();
    expect(within(metric('📈 SLA Compliance')).getByText('100%')).toBeInTheDocument(); // compliance unchanged
  });

  it('shows the amber banner and count for a warning on a still-open incident', async () => {
    incidentApi.listVisibleIncidents.mockResolvedValue([incident({ id: 'A', status: 'IN_PROGRESS' })]);
    slaApi.getViolations.mockResolvedValue([]);
    slaApi.getAtRisk.mockResolvedValue([{ incidentId: 'A' }, { incidentId: 'GONE' }]);
    renderPage();

    expect(await screen.findByText('1 incident is close to breaching the SLA')).toBeInTheDocument();
    expect(within(metric('⚠️ SLA At-Risk')).getByText('1')).toBeInTheDocument();
  });

  it('raises the red banner for a breach on a still-open incident', async () => {
    incidentApi.listVisibleIncidents.mockResolvedValue([incident({ id: 'A', status: 'IN_PROGRESS' })]);
    slaApi.getViolations.mockResolvedValue([{ incidentId: 'A' }]);
    renderPage();

    expect(await screen.findByText('1 open incident is in SLA breach')).toBeInTheDocument();
  });

  it('shows technician workload from the dashboard endpoint', async () => {
    dashboardApi.getTechnicianWorkloads.mockResolvedValue([
      { technicianId: 'T1', technicianName: 'Alex', activeIncidents: 4, maxActiveIncidents: 5, capacityUtilization: 0.8 },
      { technicianId: 'T2', technicianName: 'Sam', activeIncidents: 0, maxActiveIncidents: 4, capacityUtilization: 0 },
    ]);
    renderPage();

    const rows = await screen.findAllByTestId('workload-row');
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText('Alex')).toBeInTheDocument();
    expect(within(rows[0]).getByText('4/5')).toBeInTheDocument();
    expect(within(rows[1]).getByText('0/4')).toBeInTheDocument();
    expect(screen.getByText('Service Incident Distribution')).toBeInTheDocument();
  });

  it('shows an empty workload state and still renders when the workload endpoint fails', async () => {
    dashboardApi.getTechnicianWorkloads.mockRejectedValue(new Error('boom'));
    renderPage();

    expect(await screen.findByText('No technicians found.')).toBeInTheDocument();
    expect(screen.getByText('ITSM Service Desk Overview')).toBeInTheDocument();
  });

  it('keeps the SLA tiles clickable', async () => {
    renderPage();
    await screen.findByText('🚨 SLA Violations');

    expect(metric('🚨 SLA Violations').closest('a')).toHaveAttribute('href', '/sla');
  });
});

describe.each(['ADMINISTRATOR', 'IT_MANAGER'])('Awaiting Closure on the %s dashboard', (role) => {
  beforeEach(() => {
    auth.value = asRole(role);
    incidentApi.listVisibleIncidents.mockResolvedValue([
      incident({ id: 'A', status: 'IN_PROGRESS' }),
      incident({ id: 'B', status: 'RESOLVED', resolvedAt: '2026-01-01T00:00:00Z' }),
      incident({ id: 'V1', status: 'VERIFIED', resolvedAt: '2026-01-01T00:00:00Z' }),
      incident({ id: 'V2', status: 'VERIFIED', resolvedAt: '2026-01-01T00:00:00Z' }),
      incident({ id: 'C', status: 'CLOSED', resolvedAt: '2026-01-01T00:00:00Z' }),
    ]);
  });

  it('shows VERIFIED incidents as a separate Awaiting Closure count, not inside Resolved', async () => {
    renderPage();

    await screen.findByText('Awaiting Closure');
    expect(within(metric('Awaiting Closure')).getByText('2')).toBeInTheDocument();
    // Resolved now means resolved or closed only (B + C)
    expect(within(metric('Resolved')).getByText('2')).toBeInTheDocument();
  });

  it('drops an incident from Awaiting Closure once it is CLOSED', async () => {
    incidentApi.listVisibleIncidents.mockResolvedValue([
      incident({ id: 'V1', status: 'CLOSED', resolvedAt: '2026-01-01T00:00:00Z' }),
    ]);
    renderPage();

    await screen.findByText('Awaiting Closure');
    expect(within(metric('Awaiting Closure')).getByText('0')).toBeInTheDocument();
    expect(within(metric('Resolved')).getByText('1')).toBeInTheDocument();
  });
});

describe('Awaiting Closure is not shown to other roles', () => {
  it('a technician dashboard has no Awaiting Closure tile', async () => {
    auth.value = asRole('TECHNICIAN');
    incidentApi.listVisibleIncidents.mockResolvedValue([incident({ id: 'V1', status: 'VERIFIED' })]);
    renderPage();

    await screen.findByText('Awaiting Acknowledgement');
    expect(screen.queryByText('Awaiting Closure')).not.toBeInTheDocument();
  });
});

describe.each(['EMPLOYEE', 'TECHNICIAN', 'TEAM_LEAD', 'ADMINISTRATOR', 'IT_MANAGER'])('Greeting for %s', (role) => {
  beforeEach(() => {
    incidentApi.listVisibleIncidents.mockResolvedValue([]);
  });

  it('shows the display name first, then the login id, and keeps the role badge', async () => {
    auth.value = { ...asRole(role), currentUser: { id: 'emp1', name: 'Ganesh', role } };
    renderPage();

    expect(await screen.findByText('Ganesh (emp1)')).toBeInTheDocument();
    expect(screen.getByText(role)).toBeInTheDocument();
  });

  it('shows the login id only once when no distinct display name is available', async () => {
    auth.value = { ...asRole(role), currentUser: { id: 'emp1', name: 'emp1', role } };
    renderPage();

    expect(await screen.findByText('emp1')).toBeInTheDocument();
    expect(screen.queryByText('emp1 (emp1)')).not.toBeInTheDocument();
    expect(screen.getByText(role)).toBeInTheDocument();
  });
});
