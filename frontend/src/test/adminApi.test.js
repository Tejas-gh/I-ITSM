import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

async function loadClient(mock) {
  vi.resetModules();
  vi.stubEnv('VITE_USE_MOCKS', mock ? 'true' : 'false');
  return import('../api/apiClient');
}

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

describe('A5 duration helpers', () => {
  afterEach(() => vi.unstubAllEnvs());

  it('parseDurationSeconds reads ISO-8601 strings and numeric seconds', async () => {
    const api = await loadClient(false);
    expect(api.parseDurationSeconds('PT5M')).toBe(300);
    expect(api.parseDurationSeconds('PT2H')).toBe(7200);
    expect(api.parseDurationSeconds('PT1H30M')).toBe(5400);
    expect(api.parseDurationSeconds('PT1800S')).toBe(1800);
    expect(api.parseDurationSeconds(300)).toBe(300);
    expect(api.parseDurationSeconds('300.000000000')).toBe(300);
  });

  it('parseDurationSeconds returns null for unreadable values', async () => {
    const api = await loadClient(false);
    expect(api.parseDurationSeconds(null)).toBeNull();
    expect(api.parseDurationSeconds('')).toBeNull();
    expect(api.parseDurationSeconds('PT')).toBeNull();
    expect(api.parseDurationSeconds('soon')).toBeNull();
  });

  it('toIsoDuration converts minutes to whole-second ISO durations', async () => {
    const api = await loadClient(false);
    expect(api.toIsoDuration(30)).toBe('PT1800S');
    expect(api.toIsoDuration(0.5)).toBe('PT30S');
  });

  it('formatDuration renders short human text', async () => {
    const api = await loadClient(false);
    expect(api.formatDuration(30)).toBe('30 s');
    expect(api.formatDuration(300)).toBe('5 min');
    expect(api.formatDuration(7200)).toBe('2 h');
    expect(api.formatDuration(5400)).toBe('1 h 30 min');
    expect(api.formatDuration(null)).toBe('-');
  });
});

describe('A5 admin API in real-backend mode', () => {
  beforeEach(() => localStorage.setItem('iitsm_jwt', 'tok'));
  afterEach(() => {
    localStorage.clear();
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('reads and switches the assignment strategy', async () => {
    const api = await loadClient(false);
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(json({ selected: 'skill-based', available: ['skill-based', 'load-based'] }))
      .mockResolvedValueOnce(json({ selected: 'load-based', available: ['skill-based', 'load-based'] }));
    vi.stubGlobal('fetch', fetchMock);

    expect((await api.adminApi.getAssignmentStrategy()).selected).toBe('skill-based');
    const switched = await api.adminApi.selectAssignmentStrategy('load-based');

    expect(switched.selected).toBe('load-based');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/assignment-strategy');
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer tok');
    expect(fetchMock.mock.calls[1][0]).toBe('/api/admin/assignment-strategy');
    expect(fetchMock.mock.calls[1][1].method).toBe('PUT');
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ strategy: 'load-based' });
  });

  it('lists SLA policies and saves one with ISO durations (default policy sends serviceId null)', async () => {
    const api = await loadClient(false);
    const fetchMock = vi.fn().mockResolvedValue(json([]));
    vi.stubGlobal('fetch', fetchMock);

    await api.adminApi.listSlaPolicies();
    await api.adminApi.saveSlaPolicy({ serviceId: '', priority: 'P1', responseMinutes: 2, resolutionMinutes: 20 });
    await api.adminApi.saveSlaPolicy({ serviceId: 'SVC1', priority: 'P2', responseMinutes: 10, resolutionMinutes: 90 });

    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/sla-policies');
    expect(fetchMock.mock.calls[1][1].method).toBe('POST');
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({
      serviceId: null, priority: 'P1', responseTarget: 'PT120S', resolutionTarget: 'PT1200S',
    });
    expect(JSON.parse(fetchMock.mock.calls[2][1].body)).toEqual({
      serviceId: 'SVC1', priority: 'P2', responseTarget: 'PT600S', resolutionTarget: 'PT5400S',
    });
  });

  it('lists, creates and updates users and sets technician availability', async () => {
    const api = await loadClient(false);
    const fetchMock = vi.fn().mockResolvedValue(json({ id: 'TECH09' }));
    vi.stubGlobal('fetch', fetchMock);

    await api.adminApi.listUsers();
    await api.adminApi.createUser({ id: 'TECH09', name: 'T', email: 't@x.com', role: 'TECHNICIAN', password: 'pw' });
    await api.adminApi.updateUser('TECH09', { active: false });
    await api.adminApi.setTechnicianAvailability('TECH09', false);

    const calls = fetchMock.mock.calls;
    expect(calls[0][0]).toBe('/api/admin/users');
    expect(calls[1][0]).toBe('/api/admin/users');
    expect(calls[1][1].method).toBe('POST');
    expect(JSON.parse(calls[1][1].body).password).toBe('pw');
    expect(calls[2][0]).toBe('/api/admin/users/TECH09');
    expect(calls[2][1].method).toBe('PUT');
    expect(JSON.parse(calls[2][1].body)).toEqual({ active: false });
    expect(calls[3][0]).toBe('/api/admin/technicians/TECH09/availability');
    expect(calls[3][1].method).toBe('PUT');
    expect(JSON.parse(calls[3][1].body)).toEqual({ available: false });
  });

  it('auditApi.listAll calls GET /api/audit', async () => {
    const api = await loadClient(false);
    const fetchMock = vi.fn().mockResolvedValue(json([]));
    vi.stubGlobal('fetch', fetchMock);

    await api.auditApi.listAll();

    expect(fetchMock.mock.calls[0][0]).toBe('/api/audit');
  });

  it('non-administrator 403 errors are preserved for every admin call', async () => {
    const api = await loadClient(false);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ code: 'UNAUTHORIZED_OPERATION', message: 'admin only' }, 403)));

    await expect(api.adminApi.getAssignmentStrategy()).rejects.toMatchObject({ status: 403, code: 'UNAUTHORIZED_OPERATION' });
    await expect(api.adminApi.listUsers()).rejects.toMatchObject({ status: 403 });
  });

  it('a backend validation error (400) is surfaced with its message', async () => {
    const api = await loadClient(false);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ code: 'INVALID_INPUT', message: 'Unknown strategy: x' }, 400)));

    await expect(api.adminApi.selectAssignmentStrategy('x')).rejects.toMatchObject({
      status: 400, message: 'Unknown strategy: x',
    });
  });
});

describe('A5 admin API in mock mode', () => {
  afterEach(() => vi.unstubAllEnvs());

  it('switches strategy, rejects unknown ones, and upserts SLA policies', async () => {
    const { adminApi } = await loadClient(true);

    expect((await adminApi.selectAssignmentStrategy('load-based')).selected).toBe('load-based');
    await expect(adminApi.selectAssignmentStrategy('nope')).rejects.toThrow(/Unknown assignment strategy/);

    const before = await adminApi.listSlaPolicies();
    await adminApi.saveSlaPolicy({ serviceId: '', priority: 'P1', responseMinutes: 2, resolutionMinutes: 20 });
    const after = await adminApi.listSlaPolicies();
    expect(after).toHaveLength(before.length);
    expect(after.find((p) => p.serviceId === null && p.priority === 'P1').responseTarget).toBe('PT120S');
  });
});
