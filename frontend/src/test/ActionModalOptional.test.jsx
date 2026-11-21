import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ActionModal from '../components/ActionModal';

const renderModal = (props) =>
  render(<ActionModal isOpen title="Reopen" actionLabel="Reopen Incident" actionType="text" onClose={() => {}} {...props} />);

describe('ActionModal optional text', () => {
  it('lets an optional text modal be submitted blank', async () => {
    const onConfirm = vi.fn().mockResolvedValue();
    renderModal({ optional: true, onConfirm });

    await userEvent.setup().click(screen.getByRole('button', { name: 'Reopen Incident' }));

    expect(onConfirm).toHaveBeenCalledWith('');
  });

  it('passes the typed remark to onConfirm', async () => {
    const onConfirm = vi.fn().mockResolvedValue();
    const user = userEvent.setup();
    renderModal({ optional: true, onConfirm });

    await user.type(screen.getByPlaceholderText('Enter details...'), 'Still broken');
    await user.click(screen.getByRole('button', { name: 'Reopen Incident' }));

    expect(onConfirm).toHaveBeenCalledWith('Still broken');
  });

  it('still requires text for the other text modals (e.g. Additional Info)', async () => {
    const onConfirm = vi.fn();
    renderModal({ onConfirm });

    expect(screen.getByPlaceholderText('Enter details...')).toBeRequired();
  });
});
