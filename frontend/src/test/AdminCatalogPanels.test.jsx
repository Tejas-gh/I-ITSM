import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

vi.mock('../api/apiClient', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    adminApi: {
      listServices: vi.fn(),
      saveService: vi.fn(),
      listCategories: vi.fn(),
      saveCategory: vi.fn(),
      setCategoryActive: vi.fn(),
    },
  };
});

import { adminApi } from '../api/apiClient';
import ServicesPanel from '../components/admin/ServicesPanel';
import CategoriesPanel from '../components/admin/CategoriesPanel';
import AdminConsolePage from '../pages/AdminConsolePage';

beforeEach(() => vi.clearAllMocks());

describe('ServicesPanel', () => {
  const services = [
    { id: 'SVC_NETWORK', name: 'Corporate Network', businessCriticality: 'CRITICAL' },
    { id: 'SVC_STORAGE', name: 'Cloud Storage', businessCriticality: 'MEDIUM' },
  ];

  beforeEach(() => {
    adminApi.listServices.mockResolvedValue(services);
    adminApi.saveService.mockResolvedValue({});
  });

  it('lists services with id, name and business criticality', async () => {
    render(<ServicesPanel />);

    const rows = await screen.findAllByTestId('service-row');
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText('SVC_NETWORK')).toBeInTheDocument();
    expect(within(rows[0]).getByText('Corporate Network')).toBeInTheDocument();
    expect(within(rows[0]).getByText('CRITICAL')).toBeInTheDocument();
  });

  it('shows an empty state and a load error', async () => {
    adminApi.listServices.mockResolvedValueOnce([]);
    const { unmount } = render(<ServicesPanel />);
    expect(await screen.findByText('No services defined.')).toBeInTheDocument();
    unmount();

    adminApi.listServices.mockRejectedValueOnce(new Error('Administrator access required'));
    render(<ServicesPanel />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Administrator access required');
  });

  it('creates a service with trimmed values and reloads the list', async () => {
    const user = userEvent.setup();
    render(<ServicesPanel />);
    await screen.findAllByTestId('service-row');

    await user.click(screen.getByRole('button', { name: 'New service' }));
    await user.type(screen.getByLabelText('Service ID'), '  SVC_HR ');
    await user.type(screen.getByLabelText('Name'), ' HR Portal ');
    await user.selectOptions(screen.getByLabelText('Business criticality'), 'HIGH');
    await user.click(screen.getByRole('button', { name: 'Create service' }));

    await waitFor(() => expect(adminApi.saveService).toHaveBeenCalledWith({
      id: 'SVC_HR', name: 'HR Portal', businessCriticality: 'HIGH',
    }));
    expect(await screen.findByRole('status')).toHaveTextContent('Created SVC_HR');
    expect(adminApi.listServices).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole('form', { name: 'Service form' })).not.toBeInTheDocument();
  });

  it('edits a service: id is locked and the form is prefilled', async () => {
    const user = userEvent.setup();
    render(<ServicesPanel />);

    await user.click(await screen.findByRole('button', { name: 'Edit SVC_STORAGE' }));

    expect(screen.getByLabelText('Service ID')).toBeDisabled();
    expect(screen.getByLabelText('Service ID')).toHaveValue('SVC_STORAGE');
    expect(screen.getByLabelText('Business criticality')).toHaveValue('MEDIUM');

    const name = screen.getByLabelText('Name');
    await user.clear(name);
    await user.type(name, 'Cloud File Storage');
    await user.selectOptions(screen.getByLabelText('Business criticality'), 'HIGH');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(adminApi.saveService).toHaveBeenCalledWith({
      id: 'SVC_STORAGE', name: 'Cloud File Storage', businessCriticality: 'HIGH',
    }));
    expect(await screen.findByRole('status')).toHaveTextContent('Updated SVC_STORAGE');
  });

  it('rejects a blank id or name without calling the backend', async () => {
    const user = userEvent.setup();
    render(<ServicesPanel />);
    await screen.findAllByTestId('service-row');

    await user.click(screen.getByRole('button', { name: 'New service' }));
    await user.click(screen.getByRole('button', { name: 'Create service' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Service ID is required');

    await user.type(screen.getByLabelText('Service ID'), 'SVC_X');
    await user.click(screen.getByRole('button', { name: 'Create service' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Service name is required');

    expect(adminApi.saveService).not.toHaveBeenCalled();
  });

  it('shows the backend error when saving fails and keeps the form open', async () => {
    adminApi.saveService.mockRejectedValue(new Error('Administrator access required'));
    const user = userEvent.setup();
    render(<ServicesPanel />);
    await screen.findAllByTestId('service-row');

    await user.click(screen.getByRole('button', { name: 'New service' }));
    await user.type(screen.getByLabelText('Service ID'), 'SVC_X');
    await user.type(screen.getByLabelText('Name'), 'X');
    await user.click(screen.getByRole('button', { name: 'Create service' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Administrator access required');
    expect(screen.getByRole('form', { name: 'Service form' })).toBeInTheDocument();
  });
});

describe('CategoriesPanel', () => {
  const categories = [
    { id: 'NETWORK', name: 'Network', active: true },
    { id: 'LEGACY', name: 'Legacy Systems', active: false },
  ];

  beforeEach(() => {
    adminApi.listCategories.mockResolvedValue(categories);
    adminApi.saveCategory.mockResolvedValue({});
    adminApi.setCategoryActive.mockResolvedValue({});
  });

  it('lists categories with their active status, including inactive ones', async () => {
    render(<CategoriesPanel />);

    const rows = await screen.findAllByTestId('category-row');
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText('Active')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Inactive')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Deactivate NETWORK' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Activate LEGACY' })).toBeInTheDocument();
  });

  it('shows an empty state and a load error', async () => {
    adminApi.listCategories.mockResolvedValueOnce([]);
    const { unmount } = render(<CategoriesPanel />);
    expect(await screen.findByText('No categories defined.')).toBeInTheDocument();
    unmount();

    adminApi.listCategories.mockRejectedValueOnce(new Error('Forbidden'));
    render(<CategoriesPanel />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Forbidden');
  });

  it('creates an active category and reloads', async () => {
    const user = userEvent.setup();
    render(<CategoriesPanel />);
    await screen.findAllByTestId('category-row');

    await user.click(screen.getByRole('button', { name: 'New category' }));
    await user.type(screen.getByLabelText('Category ID'), 'CLOUD_OPS');
    await user.type(screen.getByLabelText('Name'), 'Cloud Operations');
    await user.click(screen.getByRole('button', { name: 'Create category' }));

    await waitFor(() => expect(adminApi.saveCategory).toHaveBeenCalledWith({
      id: 'CLOUD_OPS', name: 'Cloud Operations', active: true,
    }));
    expect(await screen.findByRole('status')).toHaveTextContent('Created CLOUD_OPS');
    expect(adminApi.listCategories).toHaveBeenCalledTimes(2);
  });

  it.each(['network', '1ABC', 'NET WORK', 'NET-WORK', ''])(
    'rejects the invalid category id %j without calling the backend',
    async (badId) => {
      const user = userEvent.setup();
      render(<CategoriesPanel />);
      await screen.findAllByTestId('category-row');

      await user.click(screen.getByRole('button', { name: 'New category' }));
      if (badId) await user.type(screen.getByLabelText('Category ID'), badId);
      await user.type(screen.getByLabelText('Name'), 'Some name');
      await user.click(screen.getByRole('button', { name: 'Create category' }));

      expect(await screen.findByRole('alert')).toHaveTextContent('Category ID must start with a capital letter');
      expect(adminApi.saveCategory).not.toHaveBeenCalled();
    }
  );

  it('rejects a blank name without calling the backend', async () => {
    const user = userEvent.setup();
    render(<CategoriesPanel />);
    await screen.findAllByTestId('category-row');

    await user.click(screen.getByRole('button', { name: 'New category' }));
    await user.type(screen.getByLabelText('Category ID'), 'OK_ID');
    await user.click(screen.getByRole('button', { name: 'Create category' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Category name is required');
    expect(adminApi.saveCategory).not.toHaveBeenCalled();
  });

  it('renaming an inactive category keeps it inactive (active flag is sent)', async () => {
    const user = userEvent.setup();
    render(<CategoriesPanel />);

    await user.click(await screen.findByRole('button', { name: 'Edit LEGACY' }));
    expect(screen.getByLabelText('Category ID')).toBeDisabled();
    const name = screen.getByLabelText('Name');
    await user.clear(name);
    await user.type(name, 'Legacy Platforms');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(adminApi.saveCategory).toHaveBeenCalledWith({
      id: 'LEGACY', name: 'Legacy Platforms', active: false,
    }));
  });

  it('deactivates an active category and activates an inactive one', async () => {
    const user = userEvent.setup();
    render(<CategoriesPanel />);

    await user.click(await screen.findByRole('button', { name: 'Deactivate NETWORK' }));
    expect(adminApi.setCategoryActive).toHaveBeenCalledWith('NETWORK', false);
    expect(await screen.findByRole('status')).toHaveTextContent('NETWORK deactivated');

    await user.click(screen.getByRole('button', { name: 'Activate LEGACY' }));
    expect(adminApi.setCategoryActive).toHaveBeenCalledWith('LEGACY', true);
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('LEGACY activated'));
  });

  it('shows the backend error when an operation fails', async () => {
    adminApi.setCategoryActive.mockRejectedValue(new Error('Category not found: NETWORK'));
    const user = userEvent.setup();
    render(<CategoriesPanel />);

    await user.click(await screen.findByRole('button', { name: 'Deactivate NETWORK' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Category not found: NETWORK');
  });
});

describe('AdminConsolePage (A6 tabs)', () => {
  beforeEach(() => {
    adminApi.listServices.mockResolvedValue([]);
    adminApi.listCategories.mockResolvedValue([]);
  });

  it('opens the Services and Categories panels from their tabs', async () => {
    const user = userEvent.setup();
    render(<AdminConsolePage />);

    await user.click(screen.getByRole('tab', { name: 'Services' }));
    expect(await screen.findByRole('region', { name: 'Services' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Services' })).toHaveAttribute('aria-selected', 'true');

    await user.click(screen.getByRole('tab', { name: 'Categories' }));
    expect(await screen.findByRole('region', { name: 'Categories' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Categories' })).toHaveAttribute('aria-selected', 'true');
  });
});
