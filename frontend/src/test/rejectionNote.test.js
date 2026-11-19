import { describe, it, expect, vi, afterEach } from 'vitest';
import { noteLabel, REJECTION_MARKER } from '../components/noteLabel';

describe('noteLabel', () => {
  it('labels a rejection note as RESOLUTION REJECTED regardless of its note type', () => {
    expect(noteLabel({ type: 'ADDITIONAL_INFO', text: `${REJECTION_MARKER}: reopened` })).toBe('RESOLUTION REJECTED');
    expect(noteLabel({ type: 'INVESTIGATION', text: `${REJECTION_MARKER}: reopened` })).toBe('RESOLUTION REJECTED');
  });

  it('keeps the readable note type for every other note', () => {
    expect(noteLabel({ type: 'INFO_REQUEST', text: 'Which VPN client?' })).toBe('INFO REQUEST');
    expect(noteLabel({ type: 'INVESTIGATION', text: 'mentions RESOLUTION_REJECTED later' })).toBe('INVESTIGATION');
  });

  it('is safe for missing fields', () => {
    expect(noteLabel(undefined)).toBe('');
    expect(noteLabel({})).toBe('');
  });
});

describe('mock-mode rejectResolution', () => {
  afterEach(() => vi.unstubAllEnvs());

  it('reopens the incident and records the rejection note in its history', async () => {
    vi.resetModules();
    vi.stubEnv('VITE_USE_MOCKS', 'true');
    const { incidentApi } = await import('../api/apiClient');
    const admin = { id: 'ADMIN01', role: 'ADMINISTRATOR' };
    const [target] = await incidentApi.listVisibleIncidents(admin);

    const updated = await incidentApi.rejectResolution(target.id);

    expect(updated.status).toBe('IN_PROGRESS');
    expect(updated.resolution).toBeNull();
    const last = updated.notes[updated.notes.length - 1];
    expect(last.text.startsWith(REJECTION_MARKER)).toBe(true);
    expect(last.authorId).toBe(target.reporterId);
  });
});
