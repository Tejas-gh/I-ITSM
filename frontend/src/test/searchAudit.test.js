import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

// Real-backend mode client (the default test setup uses mock mode, see vite.config.js).
async function loadRealClient() {
  vi.resetModules();
  vi.stubEnv('VITE_USE_MOCKS', 'false');
  return import('../api/apiClient');
}

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('A4 incident search query building', () => {
  it('buildSearchQuery returns an empty string when no criterion is set', async () => {
    const api = await loadRealClient();
    expect(api.buildSearchQuery({})).toBe('');
    expect(api.buildSearchQuery({ status: '', incidentId: '   ', priority: null })).toBe('');
  });

  it('buildSearchQuery includes only non-blank known criteria and trims values', async () => {
    const api = await loadRealClient();
    const qs = api.buildSearchQuery({ incidentId: ' INC1001 ', status: 'ASSIGNED', unknown: 'x', categoryId: '' });
    const params = new URLSearchParams(qs);
    expect(params.get('incidentId')).toBe('INC1001');
    expect(params.get('status')).toBe('ASSIGNED');
    expect(params.has('unknown')).toBe(false);
    expect(params.has('categoryId')).toBe(false);
  });

  it('dateToInstant converts a date input to the start or end of that UTC day', async () => {
    const api = await loadRealClient();
    expect(api.dateToInstant('2026-11-16')).toBe('2026-11-16T00:00:00Z');
    expect(api.dateToInstant('2026-11-16', true)).toBe('2026-11-16T23:59:59.999Z');
    expect(api.dateToInstant('')).toBe('');
  });
});

describe('A4 API client in real-backend mode', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('listVisibleIncidents sends all search criteria to the backend as query parameters', async () => {
    const api = await loadRealClient();
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
    vi.stubGlobal('fetch', fetchMock);

    await api.incidentApi.listVisibleIncidents(
      { id: 'ADMIN01', role: 'ADMINISTRATOR' },
      {
        incidentId: 'INC1001',
        createdFrom: '2026-11-01T00:00:00Z',
        createdTo: '2026-11-30T23:59:59.999Z',
        status: 'IN_PROGRESS',
        priority: 'P1',
        categoryId: 'NETWORK',
        serviceId: 'SVC1',
        technicianId: 'TECH01',
        slaStatus: 'BREACHED',
      }
    );

    const [url] = fetchMock.mock.calls[0];
    expect(url.startsWith('/api/incidents?')).toBe(true);
    const params = new URL(url, 'http://localhost').searchParams;
    expect(Object.fromEntries(params)).toEqual({
      incidentId: 'INC1001',
      createdFrom: '2026-11-01T00:00:00Z',
      createdTo: '2026-11-30T23:59:59.999Z',
      status: 'IN_PROGRESS',
      priority: 'P1',
      categoryId: 'NETWORK',
      serviceId: 'SVC1',
      technicianId: 'TECH01',
      slaStatus: 'BREACHED',
    });
  });

  it('listVisibleIncidents without criteria still calls plain /api/incidents (backward compatible)', async () => {
    const api = await loadRealClient();
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
    vi.stubGlobal('fetch', fetchMock);

    await api.incidentApi.listVisibleIncidents({ id: 'EMP01', role: 'EMPLOYEE' });

    expect(fetchMock.mock.calls[0][0]).toBe('/api/incidents');
  });

  it('a backend validation error (400) on search is surfaced with its message', async () => {
    const api = await loadRealClient();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({ code: 'INVALID_INPUT', message: 'createdFrom must not be after createdTo' }, 400)));

    await expect(api.incidentApi.listVisibleIncidents({ id: 'A', role: 'ADMINISTRATOR' }, { createdFrom: 'x' }))
      .rejects.toMatchObject({ status: 400, message: 'createdFrom must not be after createdTo' });
  });

  it('auditApi.listForIncident calls the incident audit endpoint with the bearer token', async () => {
    const api = await loadRealClient();
    localStorage.setItem('iitsm_jwt', 'tok');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([{ action: 'INCIDENT_CREATED' }]));
    vi.stubGlobal('fetch', fetchMock);

    const result = await api.auditApi.listForIncident('INC1001');

    expect(result).toHaveLength(1);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/audit/incidents/INC1001');
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer tok');
  });

  it('auditApi preserves the 403 returned to non-administrators', async () => {
    const api = await loadRealClient();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({ code: 'UNAUTHORIZED_OPERATION', message: 'admin only' }, 403)));

    await expect(api.auditApi.listForIncident('INC1001')).rejects.toMatchObject({
      status: 403,
      code: 'UNAUTHORIZED_OPERATION',
    });
  });
});

describe('A4 mock-mode search (local store)', () => {
  afterEach(() => vi.unstubAllEnvs());

  it('applies the status and priority criteria on top of role visibility', async () => {
    vi.resetModules();
    vi.stubEnv('VITE_USE_MOCKS', 'true');
    const { incidentApi } = await import('../api/apiClient');
    const admin = { id: 'ADMIN01', role: 'ADMINISTRATOR' };
    const all = await incidentApi.listVisibleIncidents(admin);
    expect(all.length).toBeGreaterThan(0);

    const target = all[0];
    const filtered = await incidentApi.listVisibleIncidents(admin, { status: target.status, priority: target.priority });
    expect(filtered.length).toBeGreaterThan(0);
    expect(filtered.every((i) => i.status === target.status && i.priority === target.priority)).toBe(true);

    const none = await incidentApi.listVisibleIncidents(admin, { incidentId: 'DOES-NOT-EXIST' });
    expect(none).toEqual([]);
  });
});
