import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

const auth = vi.hoisted(() => ({ value: {} }));
vi.mock('../context/AuthContext', () => ({ useAuth: () => auth.value }));
vi.mock('../api/apiClient', () => ({
  USE_MOCK: false,
  notificationApi: { getNotifications: vi.fn(), markAsRead: vi.fn() },
  incidentApi: { listVisibleIncidents: vi.fn() },
}));

import { notificationApi, incidentApi } from '../api/apiClient';
import NotificationBell from '../components/NotificationBell';

// The VERIFIED event, its notification and the incident's verifiedAt share one timestamp (default note createdAt below)
const VERIFIED_AT = '2026-01-01T10:00:00Z';
const asRole = (role) => ({ currentUser: { id: 'U1', name: 'User One', role } });
const note = (over) => ({
  id: 'N1', recipientId: 'U1', title: 'Incident Updated', body: 'Incident INC1 status has been updated.',
  incidentId: 'INC1', channelName: 'IN_APP', read: false, createdAt: '2026-01-01T10:00:00Z', ...over,
});
const assigned = (over) => note({ title: 'Incident Assigned', body: 'Incident INC1 has been assigned.', ...over });

const renderBell = () => render(<MemoryRouter><NotificationBell /></MemoryRouter>);
const tileValue = (label) => screen.getByText(label, { selector: 'span' }).closest('div').parentElement.lastElementChild.textContent;

async function openBell() {
  renderBell();
  await waitFor(() => expect(notificationApi.getNotifications).toHaveBeenCalled());
  fireEvent.click(screen.getByRole('button', { name: 'Notifications' }));
  await waitFor(() => expect(screen.getByLabelText('Notification Center')).toBeInTheDocument());
}

beforeEach(() => {
  vi.clearAllMocks();
  notificationApi.markAsRead.mockResolvedValue(undefined);
});

describe('Needs Action is driven by workflow state, not by the read flag', () => {
  beforeEach(() => {
    auth.value = asRole('TECHNICIAN');
    notificationApi.getNotifications.mockResolvedValue([assigned()]);
    incidentApi.listVisibleIncidents.mockResolvedValue([{ id: 'INC1', status: 'ASSIGNED' }]);
  });

  it('reading an assignment clears Unread but keeps Needs Action', async () => {
    await openBell();
    expect(tileValue('Unread')).toBe('1');
    expect(tileValue('Needs Action')).toBe('1');

    fireEvent.click(screen.getByTitle('Mark as read'));

    await waitFor(() => expect(tileValue('Unread')).toBe('0'));
    expect(notificationApi.markAsRead).toHaveBeenCalledWith('N1');
    expect(tileValue('Needs Action')).toBe('1');
    expect(screen.getByText('NEEDS YOUR ACTION')).toBeInTheDocument();
  });

  it('mark-all-as-read does not clear Needs Action either', async () => {
    await openBell();
    fireEvent.click(screen.getByRole('button', { name: /Mark all as read/ }));

    await waitFor(() => expect(tileValue('Unread')).toBe('0'));
    expect(tileValue('Needs Action')).toBe('1');
  });

  it('completing the workflow action (acknowledge) clears Needs Action and keeps the notification as history', async () => {
    await openBell();
    fireEvent.click(screen.getByTitle('Mark as read'));
    await waitFor(() => expect(tileValue('Unread')).toBe('0'));
    expect(tileValue('Needs Action')).toBe('1');

    // The technician acknowledges: the incident is no longer ASSIGNED
    incidentApi.listVisibleIncidents.mockResolvedValue([{ id: 'INC1', status: 'ACKNOWLEDGED' }]);
    fireEvent.click(screen.getByRole('button', { name: 'Refresh notifications' }));

    await waitFor(() => expect(tileValue('Needs Action')).toBe('0'));
    expect(screen.queryByText('NEEDS YOUR ACTION')).not.toBeInTheDocument();
    expect(screen.getByText('Incident Assigned to You')).toBeInTheDocument(); // still listed as recent activity
  });

  it('an unread assignment whose incident already moved on is not actionable', async () => {
    incidentApi.listVisibleIncidents.mockResolvedValue([{ id: 'INC1', status: 'IN_PROGRESS' }]);
    await openBell();

    expect(tileValue('Unread')).toBe('1');
    expect(tileValue('Needs Action')).toBe('0');
  });
});

describe.each(['ADMINISTRATOR', 'IT_MANAGER'])('Final closure for %s', (role) => {
  beforeEach(() => {
    auth.value = asRole(role);
  });

  it('shows a VERIFIED incident as an actionable Awaiting Closure notification that reading does not clear', async () => {
    notificationApi.getNotifications.mockResolvedValue([note()]);
    incidentApi.listVisibleIncidents.mockResolvedValue([{ id: 'INC1', status: 'VERIFIED', verifiedAt: VERIFIED_AT }]);
    await openBell();

    expect(tileValue('Needs Action')).toBe('1');
    expect(screen.getByText('NEEDS YOUR ACTION')).toBeInTheDocument();
    expect(screen.getByText('Incident Verified — Awaiting Closure')).toBeInTheDocument();
    expect(screen.getByText('Awaiting Closure')).toBeInTheDocument();

    fireEvent.click(screen.getByTitle('Mark as read'));
    await waitFor(() => expect(tileValue('Unread')).toBe('0'));
    expect(tileValue('Needs Action')).toBe('1');
  });

  it('leaves Awaiting Closure once the incident is CLOSED and keeps closed notifications historical', async () => {
    notificationApi.getNotifications.mockResolvedValue([note()]);
    incidentApi.listVisibleIncidents.mockResolvedValue([{ id: 'INC1', status: 'VERIFIED', verifiedAt: VERIFIED_AT }]);
    await openBell();
    expect(tileValue('Needs Action')).toBe('1');

    notificationApi.getNotifications.mockResolvedValue([
      note({ id: 'N2', title: 'Incident Closed', body: 'Incident INC1 has been closed.', createdAt: '2026-01-01T11:00:00Z' }),
      note(),
    ]);
    incidentApi.listVisibleIncidents.mockResolvedValue([{ id: 'INC1', status: 'CLOSED' }]);
    fireEvent.click(screen.getByRole('button', { name: 'Refresh notifications' }));

    await waitFor(() => expect(tileValue('Needs Action')).toBe('0'));
    expect(screen.queryByText('Awaiting Closure')).not.toBeInTheDocument();
    expect(screen.queryByText('NEEDS YOUR ACTION')).not.toBeInTheDocument();
    expect(screen.getByText('Incident Closed')).toBeInTheDocument();
  });

  it('labels only the VERIFIED notification when a later note is added while the incident is VERIFIED', async () => {
    notificationApi.getNotifications.mockResolvedValue([
      note({ id: 'N3', createdAt: '2026-01-01T10:30:00Z' }), // newer generic update (e.g. a note), same wording
      note({ id: 'N1', createdAt: VERIFIED_AT }),
    ]);
    incidentApi.listVisibleIncidents.mockResolvedValue([{ id: 'INC1', status: 'VERIFIED', verifiedAt: VERIFIED_AT }]);
    await openBell();

    expect(tileValue('Needs Action')).toBe('1');
    expect(screen.getAllByText('Awaiting Closure')).toHaveLength(1);
    expect(screen.getAllByText('Incident Verified — Awaiting Closure')).toHaveLength(1);
    // the later note stays an ordinary update in Recent Activity
    expect(screen.getByText('Incident Updated')).toBeInTheDocument();
    expect(screen.getByText('RECENT ACTIVITY')).toBeInTheDocument();
  });

  it('does not guess a closure notification when the incident has no verifiedAt', async () => {
    notificationApi.getNotifications.mockResolvedValue([note()]);
    incidentApi.listVisibleIncidents.mockResolvedValue([{ id: 'INC1', status: 'VERIFIED', verifiedAt: null }]);
    await openBell();

    expect(tileValue('Needs Action')).toBe('0');
    expect(screen.queryByText('Awaiting Closure')).not.toBeInTheDocument();
  });

  it('never treats an already CLOSED incident as awaiting closure', async () => {
    notificationApi.getNotifications.mockResolvedValue([
      note({ title: 'Incident Closed', body: 'Incident INC1 has been closed.' }),
    ]);
    incidentApi.listVisibleIncidents.mockResolvedValue([{ id: 'INC1', status: 'CLOSED' }]);
    await openBell();

    expect(tileValue('Needs Action')).toBe('0');
    expect(screen.queryByText('Awaiting Closure')).not.toBeInTheDocument();
  });
});

describe('Closure action is limited to Administrator / IT Manager', () => {
  it('a technician is not shown a VERIFIED incident as an action', async () => {
    auth.value = asRole('TECHNICIAN');
    notificationApi.getNotifications.mockResolvedValue([note()]);
    incidentApi.listVisibleIncidents.mockResolvedValue([{ id: 'INC1', status: 'VERIFIED', verifiedAt: VERIFIED_AT }]);
    await openBell();

    expect(tileValue('Needs Action')).toBe('0');
    expect(within(screen.getByLabelText('Notification Center')).queryByText('Awaiting Closure')).not.toBeInTheDocument();
  });
});
