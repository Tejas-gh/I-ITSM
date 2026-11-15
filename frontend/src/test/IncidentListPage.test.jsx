import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../api/apiClient', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    incidentApi: { listVisibleIncidents: vi.fn() },
    referenceApi: { listServices: vi.fn(), listCategories: vi.fn() },
  };
});
// Stable references, like the real AuthContext (currentUser lives in state). A new object per render would
// change the page's effect dependency on every render and cause an endless search loop.
const authValue = vi.hoisted(() => ({
  currentUser: { id: 'ADMIN01', name: 'ADMIN01', role: 'ADMINISTRATOR' },
}));
vi.mock('../context/AuthContext', () => ({
  useAuth: () => authValue,
}));

import { incidentApi, referenceApi } from '../api/apiClient';
import IncidentListPage from '../pages/IncidentListPage';

const incident = (id, title) => ({
  id, title, description: 'desc', priority: 'P2', priorityScore: 20, status: 'ASSIGNED',
  categoryId: 'NETWORK', assignedTechnicianId: 'TECH01',
  resolutionDeadline: '2099-01-01T00:00:00Z', resolvedAt: null,
});

const EMPTY = {
  incidentId: '', createdFrom: '', createdTo: '', status: '', priority: '',
  categoryId: '', serviceId: '', technicianId: '', slaStatus: '',
};

function renderPage() {
  return render(
    <MemoryRouter>
      <IncidentListPage />
    </MemoryRouter>
  );
}

describe('IncidentListPage server-side search', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    referenceApi.listServices.mockResolvedValue([{ id: 'SVC1', name: 'VPN' }]);
    referenceApi.listCategories.mockResolvedValue([{ id: 'NETWORK', name: 'Network' }]);
    incidentApi.listVisibleIncidents.mockResolvedValue([incident('INC1001', 'VPN down')]);
  });

  it('loads incidents from the backend with no criteria on first render', async () => {
    renderPage();

    expect(await screen.findByText('VPN down')).toBeInTheDocument();
    expect(incidentApi.listVisibleIncidents).toHaveBeenCalledWith(expect.objectContaining({ id: 'ADMIN01' }), EMPTY);
  });

  it('exposes all required search criteria', async () => {
    renderPage();
    await screen.findByText('VPN down');

    ['Incident ID', 'Created From', 'Created To', 'Status', 'Priority', 'Category', 'Service', 'Technician', 'SLA Status']
      .forEach((label) => expect(screen.getByLabelText(label)).toBeInTheDocument());
  });

  it('typing does not query; submitting sends every criterion to the backend', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('VPN down');
    incidentApi.listVisibleIncidents.mockClear();

    await user.type(screen.getByLabelText('Incident ID'), 'INC1001');
    await user.type(screen.getByLabelText('Created From'), '2026-11-01');
    await user.type(screen.getByLabelText('Created To'), '2026-11-30');
    await user.selectOptions(screen.getByLabelText('Status'), 'ASSIGNED');
    await user.selectOptions(screen.getByLabelText('Priority'), 'P2');
    await user.selectOptions(screen.getByLabelText('Category'), 'NETWORK');
    await user.selectOptions(screen.getByLabelText('Service'), 'SVC1');
    await user.type(screen.getByLabelText('Technician'), 'TECH01');
    await user.selectOptions(screen.getByLabelText('SLA Status'), 'BREACHED');
    expect(incidentApi.listVisibleIncidents).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => expect(incidentApi.listVisibleIncidents).toHaveBeenCalledTimes(1));
    expect(incidentApi.listVisibleIncidents).toHaveBeenCalledWith(expect.anything(), {
      incidentId: 'INC1001',
      createdFrom: '2026-11-01T00:00:00Z',
      createdTo: '2026-11-30T23:59:59.999Z',
      status: 'ASSIGNED',
      priority: 'P2',
      categoryId: 'NETWORK',
      serviceId: 'SVC1',
      technicianId: 'TECH01',
      slaStatus: 'BREACHED',
    });
  });

  it('displays exactly what the backend returns without filtering locally', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('VPN down');

    // The returned incident does not match the typed ID: it must still be shown (the server decides).
    incidentApi.listVisibleIncidents.mockResolvedValue([incident('INC2002', 'Printer jam')]);
    await user.type(screen.getByLabelText('Incident ID'), 'ZZZ');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('Printer jam')).toBeInTheDocument();
    expect(screen.queryByText('VPN down')).not.toBeInTheDocument();
  });

  it('shows the empty state when the backend returns no incidents', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('VPN down');

    incidentApi.listVisibleIncidents.mockResolvedValue([]);
    await user.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText(/No incidents matched/)).toBeInTheDocument();
  });

  it('shows the backend error message when the search is rejected', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('VPN down');

    incidentApi.listVisibleIncidents.mockRejectedValue(new Error('createdFrom must not be after createdTo'));
    await user.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('createdFrom must not be after createdTo');
  });

  it('reset clears the form and searches again with no criteria', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('VPN down');

    await user.type(screen.getByLabelText('Incident ID'), 'INC1001');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    await waitFor(() => expect(incidentApi.listVisibleIncidents).toHaveBeenCalledTimes(2));

    await user.click(screen.getByRole('button', { name: 'Reset' }));

    await waitFor(() => expect(incidentApi.listVisibleIncidents).toHaveBeenCalledTimes(3));
    expect(incidentApi.listVisibleIncidents).toHaveBeenLastCalledWith(expect.anything(), EMPTY);
    expect(screen.getByLabelText('Incident ID')).toHaveValue('');
  });
});
