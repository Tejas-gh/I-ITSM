import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

vi.mock('../api/apiClient', () => ({
  incidentApi: {
    getIncident: vi.fn(),
    closeIncident: vi.fn(),
  },
  escalationApi: {
    getIncidentEscalations: vi.fn().mockResolvedValue([]),
  },
  referenceApi: {
    listTechnicians: vi.fn().mockResolvedValue([]),
    listUsers: vi.fn().mockResolvedValue([]),
  },
  auditApi: {
    listForIncident: vi.fn().mockResolvedValue([]),
  },
}));

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

import { incidentApi, auditApi } from '../api/apiClient';
import { useAuth } from '../context/AuthContext';
import IncidentDetailPage from '../pages/IncidentDetailPage';

const mockIncidentBase = {
  id: 'INC1005',
  title: 'Hardware failure',
  description: 'Faulty HDMI cable',
  reporterId: 'EMP01',
  serviceId: 'SVC_HARDWARE',
  categoryId: 'HARDWARE',
  impact: 'LOW',
  urgency: 'LOW',
  priority: 'P4',
  status: 'CLOSED',
  closedAt: '2026-11-22T10:00:00Z',
  closureNote: 'Replaced cable and verified.',
  notes: [],
};

describe('IncidentDetailPage closure banner', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('displays Closed by IT Manager when closed by an IT Manager', async () => {
    useAuth.mockReturnValue({
      currentUser: { id: 'ITMGR01', name: 'Patricia Chen', role: 'IT_MANAGER' },
    });

    incidentApi.getIncident.mockResolvedValue({
      ...mockIncidentBase,
      closedById: 'ITMGR01',
      closedByRole: 'IT_MANAGER',
      closedByName: 'Patricia Chen',
    });

    render(
      <MemoryRouter initialEntries={['/incidents/INC1005']}>
        <Routes>
          <Route path="/incidents/:id" element={<IncidentDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText(/Closed by IT Manager/)).toBeInTheDocument();
  });

  it('displays Closed by Administrator when closed by an Administrator', async () => {
    useAuth.mockReturnValue({
      currentUser: { id: 'admin', name: 'admin', role: 'ADMINISTRATOR' },
    });

    incidentApi.getIncident.mockResolvedValue({
      ...mockIncidentBase,
      closedById: 'admin',
      closedByRole: 'ADMINISTRATOR',
      closedByName: 'admin',
    });

    render(
      <MemoryRouter initialEntries={['/incidents/INC1005']}>
        <Routes>
          <Route path="/incidents/:id" element={<IncidentDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText(/Closed by Administrator/)).toBeInTheDocument();
  });

  it('resolves closer role from audit trail when incident object lacks closer metadata', async () => {
    useAuth.mockReturnValue({
      currentUser: { id: 'ITMGR01', name: 'Patricia Chen', role: 'IT_MANAGER' },
    });

    incidentApi.getIncident.mockResolvedValue({
      ...mockIncidentBase,
    });

    auditApi.listForIncident.mockResolvedValue([
      {
        occurredAt: '2026-11-22T10:00:00Z',
        actorId: 'ITMGR01',
        action: 'INCIDENT_CLOSED',
        newStatus: 'CLOSED',
        description: 'Incident closed',
      },
    ]);

    render(
      <MemoryRouter initialEntries={['/incidents/INC1005']}>
        <Routes>
          <Route path="/incidents/:id" element={<IncidentDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText(/Closed by IT Manager/)).toBeInTheDocument();
  });
});
