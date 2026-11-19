import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../api/apiClient', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    incidentApi: {
      ...actual.incidentApi,
      checkDuplicates: vi.fn(),
      createIncident: vi.fn(),
      suggestCategory: vi.fn().mockResolvedValue(null),
    },
    referenceApi: {
      listServices: vi.fn().mockResolvedValue([
        { id: 'SRV-PAYMENT', name: 'Payment Gateway', businessCriticality: 'CRITICAL' },
      ]),
      listActiveCategories: vi.fn().mockResolvedValue([
        { id: 'NETWORK', name: 'Network' },
        { id: 'APPLICATION', name: 'Application' },
      ]),
    },
  };
});

const authValue = vi.hoisted(() => ({
  currentUser: { id: 'EMP01', name: 'Alice Employee', role: 'EMPLOYEE' },
  isAdmin: false,
}));

vi.mock('../context/AuthContext', () => ({
  useAuth: () => authValue,
}));

import { incidentApi } from '../api/apiClient';
import CreateIncidentPage from '../pages/CreateIncidentPage';

describe('Duplicate Detection (Gate B7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders duplicate check button on CreateIncidentPage', async () => {
    render(
      <MemoryRouter>
        <CreateIncidentPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /check duplicates/i })).toBeInTheDocument();
    });
  });

  it('displays duplicate advisory banner with safe fields when candidates exist', async () => {
    incidentApi.checkDuplicates.mockResolvedValueOnce({
      hasDuplicates: true,
      candidates: [
        {
          id: 'INC1001',
          title: 'VPN connection failure when connecting remotely',
          status: 'ASSIGNED',
          score: 0.95,
          matchReason: 'Exact title match with active open incident',
        },
      ],
    });

    render(
      <MemoryRouter>
        <CreateIncidentPage />
      </MemoryRouter>
    );

    const titleInput = screen.getByPlaceholderText(/e\.g\., Payment gateway 504 timeout/i);
    fireEvent.change(titleInput, { target: { value: 'VPN connection failure' } });

    const checkBtn = screen.getByRole('button', { name: /check duplicates/i });
    fireEvent.click(checkBtn);

    await waitFor(() => {
      expect(incidentApi.checkDuplicates).toHaveBeenCalledWith(
        expect.objectContaining({
          title: 'VPN connection failure',
        })
      );
    });

    await waitFor(() => {
      expect(screen.getByTestId('duplicate-advisory-banner')).toBeInTheDocument();
      expect(screen.getByText(/Potential Duplicate Incidents Detected/i)).toBeInTheDocument();
      expect(screen.getByText('INC1001')).toBeInTheDocument();
      expect(screen.getByText('VPN connection failure when connecting remotely')).toBeInTheDocument();
      expect(screen.getByText('95% Match')).toBeInTheDocument();
      expect(screen.getByText('Exact title match with active open incident')).toBeInTheDocument();
      expect(screen.getByText('ASSIGNED')).toBeInTheDocument();
    });

    // Ensure safe fields only - no reporter or sensitive descriptions rendered
    expect(screen.queryByText(/reporter/i)).not.toBeInTheDocument();
  });

  it('allows dismissing advisory banner without blocking submission', async () => {
    incidentApi.checkDuplicates.mockResolvedValueOnce({
      hasDuplicates: true,
      candidates: [
        {
          id: 'INC1002',
          title: 'Network latency issues',
          status: 'NEW',
          score: 0.85,
          matchReason: 'Shares keyword(s): [network, latency]',
        },
      ],
    });

    render(
      <MemoryRouter>
        <CreateIncidentPage />
      </MemoryRouter>
    );

    const titleInput = screen.getByPlaceholderText(/e\.g\., Payment gateway 504 timeout/i);
    fireEvent.change(titleInput, { target: { value: 'Network latency issues' } });

    const checkBtn = screen.getByRole('button', { name: /check duplicates/i });
    fireEvent.click(checkBtn);

    await waitFor(() => {
      expect(screen.getByTestId('duplicate-advisory-banner')).toBeInTheDocument();
    });

    // Click Dismiss
    const dismissBtn = screen.getByRole('button', { name: /dismiss/i });
    fireEvent.click(dismissBtn);

    expect(screen.queryByTestId('duplicate-advisory-banner')).not.toBeInTheDocument();

    // Submission button remains enabled
    const submitBtn = screen.getByRole('button', { name: /submit incident report/i });
    expect(submitBtn).toBeEnabled();
  });

  it('shows no duplicates confirmation when query is unique', async () => {
    incidentApi.checkDuplicates.mockResolvedValueOnce({
      hasDuplicates: false,
      candidates: [],
    });

    render(
      <MemoryRouter>
        <CreateIncidentPage />
      </MemoryRouter>
    );

    const titleInput = screen.getByPlaceholderText(/e\.g\., Payment gateway 504 timeout/i);
    fireEvent.change(titleInput, { target: { value: 'Completely unique ticket title' } });

    const checkBtn = screen.getByRole('button', { name: /check duplicates/i });
    fireEvent.click(checkBtn);

    await waitFor(() => {
      expect(screen.getByText(/✓ No active duplicates detected/i)).toBeInTheDocument();
    });
  });
});
