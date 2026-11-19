import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

vi.mock('../api/apiClient', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    incidentApi: {
      getIncident: vi.fn(),
      getRecommendations: vi.fn().mockResolvedValue([]),
    },
    referenceApi: {
      listTechnicians: vi.fn().mockResolvedValue([]),
    },
    auditApi: {
      getIncidentAuditTrail: vi.fn().mockResolvedValue([]),
    },
    escalationApi: {
      getIncidentEscalations: vi.fn(),
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

import { incidentApi, escalationApi } from '../api/apiClient';
import IncidentDetailPage from '../pages/IncidentDetailPage';

describe('IncidentDetailPage Escalation Indicators (Gate B6)', () => {
  const mockIncident = {
    id: 'INC-P1-001',
    title: 'Critical Database Failure',
    description: 'Production cluster down',
    priority: 'P1',
    priorityScore: 95,
    status: 'ASSIGNED',
    serviceId: 'SVC01',
    categoryId: 'DATABASE',
    assignedTechnicianId: 'TECH01',
    createdAt: '2026-11-19T08:00:00Z',
    responseDeadline: '2026-11-19T08:15:00Z',
    resolutionDeadline: '2026-11-19T10:00:00Z',
    acknowledgedAt: null,
    resolvedAt: null,
    slaPaused: false,
    notes: [],
  };

  const mockEscalations = [
    {
      id: 1,
      incidentId: 'INC-P1-001',
      level: 1,
      targetRole: 'TEAM_LEAD',
      escalatedAt: '2026-11-19T08:15:00Z',
      reason: 'P1 incident unacknowledged at response deadline; escalated to Team Lead.',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  function renderDetailPage() {
    return render(
      <MemoryRouter initialEntries={['/incidents/INC-P1-001']}>
        <Routes>
          <Route path="/incidents/:id" element={<IncidentDetailPage />} />
        </Routes>
      </MemoryRouter>
    );
  }

  it('renders escalation badge and alert banner when incident is escalated', async () => {
    incidentApi.getIncident.mockResolvedValue(mockIncident);
    escalationApi.getIncidentEscalations.mockResolvedValue(mockEscalations);

    renderDetailPage();

    await waitFor(() => {
      expect(screen.getByText('Critical Database Failure')).toBeInTheDocument();
    });

    // Badge
    const badge = screen.getByTestId('escalation-badge');
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveTextContent('🔥 ESCALATED L1');

    // Banner
    const banner = screen.getByTestId('escalation-banner');
    expect(banner).toBeInTheDocument();
    expect(banner).toHaveTextContent(/Automatic Escalation Triggered — Level 1 \(TEAM_LEAD\)/i);
    expect(banner).toHaveTextContent(/P1 incident unacknowledged at response deadline/i);
  });

  it('does not render escalation banner or badge when incident has no escalations', async () => {
    incidentApi.getIncident.mockResolvedValue(mockIncident);
    escalationApi.getIncidentEscalations.mockResolvedValue([]);

    renderDetailPage();

    await waitFor(() => {
      expect(screen.getByText('Critical Database Failure')).toBeInTheDocument();
    });

    expect(screen.queryByTestId('escalation-badge')).not.toBeInTheDocument();
    expect(screen.queryByTestId('escalation-banner')).not.toBeInTheDocument();
  });
});
