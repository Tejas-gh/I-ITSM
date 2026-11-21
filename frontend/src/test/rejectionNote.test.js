import { describe, it, expect, vi, afterEach } from 'vitest';
import { noteLabel, mergeRejectionRemarks, REJECTION_MARKER, REJECTION_REMARK_PREFIX } from '../components/noteLabel';

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

describe('rejection remark', () => {
  const rejection = { authorId: 'EMP01', type: 'ADDITIONAL_INFO', text: `${REJECTION_MARKER}: reopened` };
  const remark = { authorId: 'EMP01', type: 'ADDITIONAL_INFO', text: `${REJECTION_REMARK_PREFIX}Still cannot log in` };

  it('mergeRejectionRemarks shows the remark inside the rejection entry', () => {
    const merged = mergeRejectionRemarks([{ authorId: 'TECH01', type: 'INVESTIGATION', text: 'Checked' }, rejection, remark]);

    expect(merged).toHaveLength(2);
    expect(merged[1].text).toBe(`${REJECTION_MARKER}: reopened\n\nRemark: Still cannot log in`);
    expect(noteLabel(merged[1])).toBe('RESOLUTION REJECTED');
  });

  it('mergeRejectionRemarks leaves a rejection without a remark, and unrelated notes, untouched', () => {
    const other = { authorId: 'EMP01', type: 'ADDITIONAL_INFO', text: 'Some other info' };

    expect(mergeRejectionRemarks([rejection, other])).toEqual([rejection, other]);
    expect(mergeRejectionRemarks([remark])).toEqual([remark]);
    expect(mergeRejectionRemarks([])).toEqual([]);
  });

  it('mergeRejectionRemarks does not attach a remark written by someone else', () => {
    const foreign = { ...remark, authorId: 'TECH01' };

    expect(mergeRejectionRemarks([rejection, foreign])).toHaveLength(2);
  });

  it('mock-mode rejectResolution stores the remark right after the rejection note (and none when blank)', async () => {
    vi.resetModules();
    vi.stubEnv('VITE_USE_MOCKS', 'true');
    const { incidentApi } = await import('../api/apiClient');
    const [first, second] = await incidentApi.listVisibleIncidents({ id: 'ADMIN01', role: 'ADMINISTRATOR' });

    const withRemark = await incidentApi.rejectResolution(first.id, '  Fix did not work  ');
    const notes = withRemark.notes;
    expect(notes[notes.length - 2].text.startsWith(REJECTION_MARKER)).toBe(true);
    expect(notes[notes.length - 1].text).toBe(`${REJECTION_REMARK_PREFIX}Fix did not work`);

    const noRemark = await incidentApi.rejectResolution(second.id, '   ');
    expect(noRemark.notes[noRemark.notes.length - 1].text.startsWith(REJECTION_MARKER)).toBe(true);
  });
});
