import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

vi.mock('../api/apiClient', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    adminApi: {
      getAssignmentStrategy: vi.fn(),
      selectAssignmentStrategy: vi.fn(),
      listSlaPolicies: vi.fn(),
      saveSlaPolicy: vi.fn(),
      listUsers: vi.fn(),
      createUser: vi.fn(),
      updateUser: vi.fn(),
      setTechnicianAvailability: vi.fn(),
    },
    referenceApi: { listServices: vi.fn() },
    auditApi: { listAll: vi.fn(), listForIncident: vi.fn() },
  };
});

import { adminApi, referenceApi, auditApi } from '../api/apiClient';
import StrategyPanel from '../components/admin/StrategyPanel';
import SlaPoliciesPanel from '../components/admin/SlaPoliciesPanel';
import UsersPanel from '../components/admin/UsersPanel';
import AuditPanel from '../components/admin/AuditPanel';
import AdminConsolePage from '../pages/AdminConsolePage';

beforeEach(() => vi.clearAllMocks());

describe('StrategyPanel', () => {
  beforeEach(() => {
    adminApi.getAssignmentStrategy.mockResolvedValue({ selected: 'skill-based', available: ['skill-based', 'load-based'] });
  });

  it('shows the current strategy clearly and offers the others', async () => {
    render(<StrategyPanel />);

    expect(await screen.findByTestId('current-strategy')).toHaveTextContent('skill-based');
    expect(within(screen.getByTestId('strategy-skill-based')).getByText('ACTIVE')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Activate load-based' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Activate skill-based' })).not.toBeInTheDocument();
  });

  it('switches strategy through the backend and shows the new selection', async () => {
    adminApi.selectAssignmentStrategy.mockResolvedValue({ selected: 'load-based', available: ['skill-based', 'load-based'] });
    const user = userEvent.setup();
    render(<StrategyPanel />);

    await user.click(await screen.findByRole('button', { name: 'Activate load-based' }));

    expect(adminApi.selectAssignmentStrategy).toHaveBeenCalledWith('load-based');
    await waitFor(() => expect(screen.getByTestId('current-strategy')).toHaveTextContent('load-based'));
    expect(screen.getByRole('status')).toHaveTextContent('switched to load-based');
  });

  it('shows the backend error when switching fails and keeps the old selection', async () => {
    adminApi.selectAssignmentStrategy.mockRejectedValue(new Error('Administrator access required'));
    const user = userEvent.setup();
    render(<StrategyPanel />);

    await user.click(await screen.findByRole('button', { name: 'Activate load-based' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Administrator access required');
    expect(screen.getByTestId('current-strategy')).toHaveTextContent('skill-based');
  });

  it('shows an error when the strategy cannot be loaded', async () => {
    adminApi.getAssignmentStrategy.mockRejectedValue(new Error('Forbidden'));
    render(<StrategyPanel />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Forbidden');
  });
});

describe('SlaPoliciesPanel', () => {
  const policies = [
    { serviceId: null, priority: 'P1', responseTarget: 'PT5M', resolutionTarget: 'PT30M' },
    { serviceId: 'SVC1', priority: 'P2', responseTarget: 'PT10M', resolutionTarget: 'PT1H30M' },
  ];

  beforeEach(() => {
    adminApi.listSlaPolicies.mockResolvedValue(policies);
    referenceApi.listServices.mockResolvedValue([{ id: 'SVC1', name: 'VPN Service' }]);
  });

  it('lists policies with scope, priority and readable response/resolution targets', async () => {
    render(<SlaPoliciesPanel />);

    const rows = await screen.findAllByTestId('sla-policy-row');
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText('Default (all services)')).toBeInTheDocument();
    expect(within(rows[0]).getByText('5 min')).toBeInTheDocument();
    expect(within(rows[0]).getByText('30 min')).toBeInTheDocument();
    expect(within(rows[1]).getByText('VPN Service')).toBeInTheDocument();
    expect(within(rows[1]).getByText('1 h 30 min')).toBeInTheDocument();
  });

  it('Edit prefills the form from the policy', async () => {
    const user = userEvent.setup();
    render(<SlaPoliciesPanel />);

    await user.click(await screen.findByRole('button', { name: 'Edit P2 policy for VPN Service' }));

    expect(screen.getByLabelText('Service')).toHaveValue('SVC1');
    expect(screen.getByLabelText('Priority')).toHaveValue('P2');
    expect(screen.getByLabelText('Response (minutes)')).toHaveValue(10);
    expect(screen.getByLabelText('Resolution (minutes)')).toHaveValue(90);
  });

  it('saves a policy through the backend and reloads the list', async () => {
    adminApi.saveSlaPolicy.mockResolvedValue({});
    const user = userEvent.setup();
    render(<SlaPoliciesPanel />);
    await screen.findAllByTestId('sla-policy-row');

    await user.selectOptions(screen.getByLabelText('Priority'), 'P1');
    await user.type(screen.getByLabelText('Response (minutes)'), '2');
    await user.type(screen.getByLabelText('Resolution (minutes)'), '20');
    await user.click(screen.getByRole('button', { name: 'Save policy' }));

    await waitFor(() => expect(adminApi.saveSlaPolicy).toHaveBeenCalledWith({
      serviceId: '', priority: 'P1', responseMinutes: 2, resolutionMinutes: 20,
    }));
    expect(await screen.findByRole('status')).toHaveTextContent('Saved P1 policy');
    expect(adminApi.listSlaPolicies).toHaveBeenCalledTimes(2);
  });

  it('rejects non-positive targets and response > resolution without calling the backend', async () => {
    const user = userEvent.setup();
    render(<SlaPoliciesPanel />);
    await screen.findAllByTestId('sla-policy-row');

    await user.click(screen.getByRole('button', { name: 'Save policy' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('must be positive');

    await user.type(screen.getByLabelText('Response (minutes)'), '60');
    await user.type(screen.getByLabelText('Resolution (minutes)'), '30');
    await user.click(screen.getByRole('button', { name: 'Save policy' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('must not exceed');

    expect(adminApi.saveSlaPolicy).not.toHaveBeenCalled();
  });

  it('shows the backend error when saving fails', async () => {
    adminApi.saveSlaPolicy.mockRejectedValue(new Error('Administrator access required'));
    const user = userEvent.setup();
    render(<SlaPoliciesPanel />);
    await screen.findAllByTestId('sla-policy-row');

    await user.type(screen.getByLabelText('Response (minutes)'), '2');
    await user.type(screen.getByLabelText('Resolution (minutes)'), '20');
    await user.click(screen.getByRole('button', { name: 'Save policy' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Administrator access required');
  });
});

describe('UsersPanel', () => {
  const users = [
    { id: 'EMP01', name: 'Jane Doe', email: 'jane@corp.com', role: 'EMPLOYEE', active: true, department: 'Finance' },
    { id: 'TECH01', name: 'Alex Smith', email: 'alex@corp.com', role: 'TECHNICIAN', active: true,
      skillLevels: { NETWORK: 3 }, yearsOfExperience: 5, maxActiveIncidents: 5, available: true },
  ];

  beforeEach(() => {
    adminApi.listUsers.mockResolvedValue(users);
    adminApi.createUser.mockResolvedValue({});
    adminApi.updateUser.mockResolvedValue({});
    adminApi.setTechnicianAvailability.mockResolvedValue({});
  });

  it('lists users with role-specific details and never a password', async () => {
    render(<UsersPanel />);

    const rows = await screen.findAllByTestId('user-row');
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText('Finance')).toBeInTheDocument();
    expect(within(rows[1]).getByText(/NETWORK:3/)).toBeInTheDocument();
    expect(within(rows[1]).getByText(/· available$/)).toBeInTheDocument();
    expect(screen.queryByText('secret123')).not.toBeInTheDocument();
  });

  it('deactivates a user through the backend', async () => {
    const user = userEvent.setup();
    render(<UsersPanel />);

    await user.click(await screen.findByRole('button', { name: 'Deactivate EMP01' }));

    expect(adminApi.updateUser).toHaveBeenCalledWith('EMP01', { active: false });
    expect(await screen.findByRole('status')).toHaveTextContent('EMP01 deactivated');
  });

  it('toggles technician availability only for technicians', async () => {
    const user = userEvent.setup();
    render(<UsersPanel />);
    await screen.findAllByTestId('user-row');

    expect(screen.queryByRole('button', { name: /Set EMP01/ })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Set TECH01 unavailable' }));

    expect(adminApi.setTechnicianAvailability).toHaveBeenCalledWith('TECH01', false);
  });

  it('creates an employee with department and password', async () => {
    const user = userEvent.setup();
    render(<UsersPanel />);
    await screen.findAllByTestId('user-row');

    await user.click(screen.getByRole('button', { name: 'New user' }));
    await user.type(screen.getByLabelText('User ID'), 'EMP09');
    await user.type(screen.getByLabelText('Name'), 'New Person');
    await user.type(screen.getByLabelText('Email'), 'new@corp.com');
    await user.type(screen.getByLabelText('Password'), 'Secret123!');
    await user.type(screen.getByLabelText('Department'), 'HR');
    await user.click(screen.getByRole('button', { name: 'Create user' }));

    await waitFor(() => expect(adminApi.createUser).toHaveBeenCalledWith({
      id: 'EMP09', name: 'New Person', email: 'new@corp.com', role: 'EMPLOYEE', password: 'Secret123!', department: 'HR',
    }));
    expect(await screen.findByRole('status')).toHaveTextContent('Created EMP09');
    expect(screen.queryByRole('form', { name: 'User form' })).not.toBeInTheDocument();
  });

  it('creates a technician with parsed skills and limits', async () => {
    const user = userEvent.setup();
    render(<UsersPanel />);
    await screen.findAllByTestId('user-row');

    await user.click(screen.getByRole('button', { name: 'New user' }));
    await user.selectOptions(screen.getByLabelText('Role'), 'TECHNICIAN');
    await user.type(screen.getByLabelText('User ID'), 'TECH09');
    await user.type(screen.getByLabelText('Name'), 'Tech Nine');
    await user.type(screen.getByLabelText('Email'), 'tech9@corp.com');
    await user.type(screen.getByLabelText('Password'), 'Secret123!');
    await user.type(screen.getByLabelText('Skills (CATEGORY:level)'), 'NETWORK:3, cloud:2');
    await user.click(screen.getByRole('button', { name: 'Create user' }));

    await waitFor(() => expect(adminApi.createUser).toHaveBeenCalledWith(expect.objectContaining({
      id: 'TECH09', role: 'TECHNICIAN', skillLevels: { NETWORK: 3, CLOUD: 2 },
      yearsOfExperience: 0, maxActiveIncidents: 5,
    })));
  });

  it('rejects invalid skills locally without calling the backend', async () => {
    const user = userEvent.setup();
    render(<UsersPanel />);
    await screen.findAllByTestId('user-row');

    await user.click(screen.getByRole('button', { name: 'New user' }));
    await user.selectOptions(screen.getByLabelText('Role'), 'TECHNICIAN');
    await user.type(screen.getByLabelText('Skills (CATEGORY:level)'), 'NETWORK:9');
    await user.click(screen.getByRole('button', { name: 'Create user' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid skill');
    expect(adminApi.createUser).not.toHaveBeenCalled();
  });

  it('edits a user: id and role are locked, update omits the password', async () => {
    const user = userEvent.setup();
    render(<UsersPanel />);

    await user.click(await screen.findByRole('button', { name: 'Edit EMP01' }));

    expect(screen.getByLabelText('User ID')).toBeDisabled();
    expect(screen.getByLabelText('Role')).toBeDisabled();
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();

    const name = screen.getByLabelText('Name');
    await user.clear(name);
    await user.type(name, 'Jane Smith');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(adminApi.updateUser).toHaveBeenCalledWith('EMP01', {
      name: 'Jane Smith', email: 'jane@corp.com', department: 'Finance',
    }));
  });

  it('shows the backend error when an operation fails', async () => {
    adminApi.updateUser.mockRejectedValue(new Error('Cannot deactivate the last administrator'));
    const user = userEvent.setup();
    render(<UsersPanel />);

    await user.click(await screen.findByRole('button', { name: 'Deactivate EMP01' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot deactivate the last administrator');
  });
});

describe('AuditPanel', () => {
  const entries = [
    { occurredAt: '2026-11-16T09:00:00Z', actorId: 'EMP01', incidentId: 'INC1001', previousStatus: null,
      newStatus: 'NEW', action: 'INCIDENT_CREATED', description: 'created' },
    { occurredAt: '2026-11-16T09:10:00Z', actorId: 'TECH01', incidentId: 'INC1001', previousStatus: 'ASSIGNED',
      newStatus: 'ACKNOWLEDGED', action: 'INCIDENT_ACKNOWLEDGED', description: 'acknowledged' },
  ];

  beforeEach(() => {
    auditApi.listAll.mockResolvedValue(entries);
    auditApi.listForIncident.mockResolvedValue([entries[0]]);
  });

  it('loads all audit entries newest first with the audit fields', async () => {
    render(<AuditPanel />);

    const rows = await screen.findAllByTestId('audit-row');
    expect(auditApi.listAll).toHaveBeenCalledTimes(1);
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText('INCIDENT_ACKNOWLEDGED')).toBeInTheDocument();
    expect(within(rows[0]).getByText('TECH01')).toBeInTheDocument();
    expect(within(rows[0]).getByText('Acknowledged')).toBeInTheDocument();
    expect(within(rows[1]).getByText('INCIDENT_CREATED')).toBeInTheDocument();
  });

  it('searches one incident server-side and resets to all entries', async () => {
    const user = userEvent.setup();
    render(<AuditPanel />);
    await screen.findAllByTestId('audit-row');

    await user.type(screen.getByLabelText('Incident ID'), 'INC1001');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    await waitFor(() => expect(screen.getAllByTestId('audit-row')).toHaveLength(1));
    expect(auditApi.listForIncident).toHaveBeenCalledWith('INC1001');

    await user.click(screen.getByRole('button', { name: 'Reset' }));
    await waitFor(() => expect(screen.getAllByTestId('audit-row')).toHaveLength(2));
    expect(auditApi.listAll).toHaveBeenCalledTimes(2);
  });

  it('shows an empty state and an error state', async () => {
    auditApi.listAll.mockResolvedValueOnce([]);
    const { unmount } = render(<AuditPanel />);
    expect(await screen.findByText('No audit entries found.')).toBeInTheDocument();
    unmount();

    auditApi.listAll.mockRejectedValueOnce(new Error('Administrator access required'));
    render(<AuditPanel />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Administrator access required');
  });
});

describe('AdminConsolePage', () => {
  beforeEach(() => {
    adminApi.getAssignmentStrategy.mockResolvedValue({ selected: 'skill-based', available: ['skill-based', 'load-based'] });
    adminApi.listSlaPolicies.mockResolvedValue([]);
    adminApi.listUsers.mockResolvedValue([]);
    referenceApi.listServices.mockResolvedValue([]);
    auditApi.listAll.mockResolvedValue([]);
  });

  it('shows the four sections as tabs, starting on Assignment Strategy', async () => {
    render(<AdminConsolePage />);

    const tabs = screen.getAllByRole('tab').map((t) => t.textContent);
    expect(tabs).toEqual(['Assignment Strategy', 'SLA Policies', 'Users', 'Audit']);
    expect(screen.getByRole('tab', { name: 'Assignment Strategy' })).toHaveAttribute('aria-selected', 'true');
    expect(await screen.findByRole('region', { name: 'Assignment strategy' })).toBeInTheDocument();
  });

  it('switches panels when a tab is selected', async () => {
    const user = userEvent.setup();
    render(<AdminConsolePage />);

    await user.click(screen.getByRole('tab', { name: 'SLA Policies' }));
    expect(await screen.findByRole('region', { name: 'SLA policies' })).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Users' }));
    expect(await screen.findByRole('region', { name: 'Users' })).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Audit' }));
    expect(await screen.findByRole('region', { name: 'Audit log' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Audit' })).toHaveAttribute('aria-selected', 'true');
  });
});
