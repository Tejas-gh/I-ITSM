import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ActionModal from '../components/ActionModal';

vi.mock('../api/apiClient', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    incidentApi: {
      getRecommendations: vi.fn(),
    },
  };
});

import { incidentApi } from '../api/apiClient';

describe('ActionModal Smart Technician Recommendations', () => {
  const mockTechs = [
    { id: 'TECH01', name: 'Alice Network' },
    { id: 'TECH02', name: 'Bob Security' },
  ];

  const mockRecommendations = [
    {
      technicianId: 'TECH01',
      name: 'Alice Network',
      score: 95.5,
      reason: 'Domain skill match (NETWORK), workload 2/5 (40%)',
      activeWorkload: 2,
      maxCapacity: 5,
      yearsOfExperience: 6,
    },
    {
      technicianId: 'TECH02',
      name: 'Bob Security',
      score: 72.0,
      reason: 'Workload 3/5 (60%)',
      activeWorkload: 3,
      maxCapacity: 5,
      yearsOfExperience: 4,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    incidentApi.getRecommendations.mockResolvedValue(mockRecommendations);
  });

  it('fetches and displays ranked smart recommendations when opened with actionType="technician"', async () => {
    const handleConfirm = vi.fn();
    const handleClose = vi.fn();

    render(
      <ActionModal
        isOpen={true}
        title="Assign Incident"
        description="Select a technician to assign this incident."
        actionLabel="Assign"
        actionType="technician"
        technicians={mockTechs}
        incidentId="INC-1001"
        onConfirm={handleConfirm}
        onClose={handleClose}
      />
    );

    expect(incidentApi.getRecommendations).toHaveBeenCalledWith('INC-1001');

    await waitFor(() => {
      expect(screen.getByText(/Smart Technician Recommendations/i)).toBeInTheDocument();
      expect(screen.getByText(/#1 Best Fit/i)).toBeInTheDocument();
      expect(screen.getByText('Alice Network')).toBeInTheDocument();
      expect(screen.getByText('Score: 95.5')).toBeInTheDocument();
      expect(screen.getByText(/Domain skill match \(NETWORK\)/i)).toBeInTheDocument();
      expect(screen.getByText('Bob Security')).toBeInTheDocument();
      expect(screen.getByText('Score: 72.0')).toBeInTheDocument();
    });
  });

  it('selects recommendation and submits chosen technician', async () => {
    const user = userEvent.setup();
    const handleConfirm = vi.fn().mockResolvedValue();
    const handleClose = vi.fn();

    render(
      <ActionModal
        isOpen={true}
        title="Assign Incident"
        description="Select a technician to assign this incident."
        actionLabel="Assign"
        actionType="technician"
        technicians={mockTechs}
        incidentId="INC-1001"
        onConfirm={handleConfirm}
        onClose={handleClose}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('Bob Security')).toBeInTheDocument();
    });

    // Click on Bob Security's card
    const bobCard = screen.getByTestId('rec-card-TECH02');
    await user.click(bobCard);

    // Click assign button
    const submitBtn = screen.getByRole('button', { name: 'Assign' });
    await user.click(submitBtn);

    expect(handleConfirm).toHaveBeenCalledWith('TECH02');
  });
});
