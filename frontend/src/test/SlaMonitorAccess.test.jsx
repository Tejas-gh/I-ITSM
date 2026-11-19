import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

const auth = vi.hoisted(() => ({ value: {} }));
vi.mock('../context/AuthContext', () => ({ useAuth: () => auth.value }));
vi.mock('../components/NotificationBell', () => ({ default: () => null }));

import Navbar from '../components/Navbar';

const user = (role) => ({ id: 'U1', name: 'U1', role });
const asRole = (role) => ({
  currentUser: user(role),
  availableUsers: [],
  selectUser: () => {},
  logout: () => {},
  isAuthenticated: true,
  isEmployee: role === 'EMPLOYEE',
  isAdmin: role === 'ADMINISTRATOR' || role === 'IT_MANAGER',
  isTechnician: role === 'TECHNICIAN' || role === 'TEAM_LEAD',
});

describe('SLA Monitor navigation by role', () => {
  beforeEach(() => vi.clearAllMocks());

  it.each([
    ['EMPLOYEE', false],
    ['TECHNICIAN', true],
    ['TEAM_LEAD', true],
    ['ADMINISTRATOR', true],
    ['IT_MANAGER', true],
  ])('%s sees the SLA Monitor link: %s', (role, visible) => {
    auth.value = asRole(role);
    render(<MemoryRouter><Navbar /></MemoryRouter>);

    const link = screen.queryByRole('link', { name: 'SLA Monitor' });
    if (visible) expect(link).toBeInTheDocument();
    else expect(link).not.toBeInTheDocument();
  });
});
