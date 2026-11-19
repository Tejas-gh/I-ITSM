import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';

const auth = vi.hoisted(() => ({ value: {} }));
vi.mock('../context/AuthContext', () => ({
  useAuth: () => auth.value,
  AuthProvider: ({ children }) => children,
}));
vi.mock('../components/Navbar', () => ({ default: () => <nav /> }));
vi.mock('../pages/LoginPage', () => ({ default: () => <div>Login page</div> }));
vi.mock('../pages/DashboardPage', () => ({ default: () => <div>Dashboard page</div> }));
vi.mock('../pages/IncidentListPage', () => ({ default: () => <div>Incident list page</div> }));
vi.mock('../pages/CreateIncidentPage', () => ({ default: () => <div>Create page</div> }));
vi.mock('../pages/IncidentDetailPage', () => ({ default: () => <div>Detail page</div> }));
vi.mock('../pages/SlaMonitorPage', () => ({ default: () => <div>SLA monitor page</div> }));
vi.mock('../pages/TechniciansPage', () => ({ default: () => <div>Technicians page</div> }));
vi.mock('../pages/AdminConsolePage', () => ({ default: () => <div>Admin page</div> }));

import App from '../App';

const asRole = (role) => ({
  isAuthenticated: true,
  isEmployee: role === 'EMPLOYEE',
  isAdmin: role === 'ADMINISTRATOR' || role === 'IT_MANAGER',
  isTechnician: role === 'TECHNICIAN' || role === 'TEAM_LEAD',
});

describe('/sla route access by role', () => {
  beforeEach(() => window.history.pushState({}, '', '/sla'));

  it('redirects an employee to the dashboard', () => {
    auth.value = asRole('EMPLOYEE');
    render(<App />);

    expect(screen.getByText('Dashboard page')).toBeInTheDocument();
    expect(screen.queryByText('SLA monitor page')).not.toBeInTheDocument();
  });

  it.each(['TECHNICIAN', 'TEAM_LEAD', 'ADMINISTRATOR', 'IT_MANAGER'])('shows the SLA monitor to %s', (role) => {
    auth.value = asRole(role);
    render(<App />);

    expect(screen.getByText('SLA monitor page')).toBeInTheDocument();
  });
});
