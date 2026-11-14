import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

// The default test setup runs in mock mode (vite.config.js). These tests load the client in real-backend mode.
async function loadRealClient() {
  vi.resetModules();
  vi.stubEnv('VITE_USE_MOCKS', 'false');
  return import('../api/apiClient');
}

function jsonResponse(body, status = 200) {
  const text = body === undefined ? '' : JSON.stringify(body);
  return new Response(text, { status, headers: { 'Content-Type': 'application/json' } });
}

function fakeJwt(claims) {
  const b64 = (o) => btoa(JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${b64({ alg: 'HS256' })}.${b64(claims)}.signature`;
}

describe('API client in real-backend mode', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('login posts credentials without a bearer token and stores the access token', async () => {
    const api = await loadRealClient();
    localStorage.setItem('iitsm_jwt', 'stale-token');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ accessToken: 'new-token', tokenType: 'Bearer' }));
    vi.stubGlobal('fetch', fetchMock);

    await api.authApi.login('EMP01', 'secret');

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/auth/login');
    expect(init.method).toBe('POST');
    expect(init.headers.Authorization).toBeUndefined();
    expect(JSON.parse(init.body)).toEqual({ id: 'EMP01', password: 'secret' });
    expect(localStorage.getItem('iitsm_jwt')).toBe('new-token');
  });

  it('authenticated calls send the stored bearer token', async () => {
    const api = await loadRealClient();
    localStorage.setItem('iitsm_jwt', 'abc');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
    vi.stubGlobal('fetch', fetchMock);

    await api.incidentApi.listVisibleIncidents({ id: 'EMP01', role: 'EMPLOYEE' });

    expect(fetchMock.mock.calls[0][0]).toBe('/api/incidents');
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer abc');
  });

  it('a 401 on an authenticated call clears the token and fires the unauthorized event', async () => {
    const api = await loadRealClient();
    localStorage.setItem('iitsm_jwt', 'expired');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ code: 'UNAUTHORIZED', message: 'nope' }, 401)));
    const listener = vi.fn();
    window.addEventListener(api.UNAUTHORIZED_EVENT, listener);

    await expect(api.incidentApi.getIncident('INC1001')).rejects.toMatchObject({ status: 401 });

    window.removeEventListener(api.UNAUTHORIZED_EVENT, listener);
    expect(localStorage.getItem('iitsm_jwt')).toBeNull();
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('a failed login surfaces the backend message and does not fire the unauthorized event', async () => {
    const api = await loadRealClient();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ code: 'INVALID_LOGIN', message: 'Invalid credentials' }, 401)));
    const listener = vi.fn();
    window.addEventListener(api.UNAUTHORIZED_EVENT, listener);

    await expect(api.authApi.login('EMP01', 'wrong')).rejects.toMatchObject({
      message: 'Invalid credentials',
      code: 'INVALID_LOGIN',
    });

    window.removeEventListener(api.UNAUTHORIZED_EVENT, listener);
    expect(listener).not.toHaveBeenCalled();
  });

  it('suggestCategory treats an empty 200 response as "no suggestion"', async () => {
    const api = await loadRealClient();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 200 })));

    await expect(api.incidentApi.suggestCategory('Printer jam', 'Paper stuck')).resolves.toBeNull();
  });

  it('administrator-only reference lists return an empty list on 403', async () => {
    const api = await loadRealClient();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ code: 'UNAUTHORIZED_OPERATION', message: 'admin only' }, 403)));

    await expect(api.referenceApi.listTechnicians()).resolves.toEqual([]);
  });

  it('reference data comes from the backend, not the mock store', async () => {
    const api = await loadRealClient();
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(jsonResponse([{ id: 'SVC1', name: 'Mail' }])));
    vi.stubGlobal('fetch', fetchMock);

    await api.referenceApi.listServices();
    await api.referenceApi.listActiveCategories();

    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual(['/api/services', '/api/categories/active']);
  });

  it('respondToInfo posts the response text to /respond-info', async () => {
    const api = await loadRealClient();
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 'INC1001' }));
    vi.stubGlobal('fetch', fetchMock);

    await api.incidentApi.respondToInfo('INC1001', 'The VPN client is v5');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/incidents/INC1001/respond-info');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ text: 'The VPN client is v5' });
  });

  it('a network failure becomes a readable NETWORK_ERROR', async () => {
    const api = await loadRealClient();
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    await expect(api.incidentApi.getIncident('INC1001')).rejects.toMatchObject({ code: 'NETWORK_ERROR' });
  });

  it('decodeJwt reads subject and role claims and rejects garbage', async () => {
    const api = await loadRealClient();
    const claims = api.decodeJwt(fakeJwt({ sub: 'ADMIN01', role: 'ADMINISTRATOR', exp: 4102444800 }));

    expect(claims).toMatchObject({ sub: 'ADMIN01', role: 'ADMINISTRATOR' });
    expect(api.decodeJwt('not-a-jwt')).toBeNull();
  });
});
