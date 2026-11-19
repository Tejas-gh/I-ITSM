import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

async function loadClient(mock) {
  vi.resetModules();
  vi.stubEnv('VITE_USE_MOCKS', mock ? 'true' : 'false');
  return import('../api/apiClient');
}

// A fresh Response per call, as a real fetch would return
const respond = (body, status = 200) => () =>
  Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));

describe('A6 services/categories admin API in real-backend mode', () => {
  beforeEach(() => localStorage.setItem('iitsm_jwt', 'tok'));
  afterEach(() => {
    localStorage.clear();
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('lists services and saves one (POST upsert) through /api/admin/services', async () => {
    const api = await loadClient(false);
    const fetchMock = vi.fn(respond([]));
    vi.stubGlobal('fetch', fetchMock);

    await api.adminApi.listServices();
    await api.adminApi.saveService({ id: 'SVC_X', name: 'X Service', businessCriticality: 'HIGH' });

    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/services');
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer tok');
    expect(fetchMock.mock.calls[1][0]).toBe('/api/admin/services');
    expect(fetchMock.mock.calls[1][1].method).toBe('POST');
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({
      id: 'SVC_X', name: 'X Service', businessCriticality: 'HIGH',
    });
  });

  it('lists categories and saves one with an explicit active flag', async () => {
    const api = await loadClient(false);
    const fetchMock = vi.fn(respond([]));
    vi.stubGlobal('fetch', fetchMock);

    await api.adminApi.listCategories();
    await api.adminApi.saveCategory({ id: 'CLOUD_OPS', name: 'Cloud Ops', active: false });

    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/categories');
    expect(fetchMock.mock.calls[1][1].method).toBe('POST');
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ id: 'CLOUD_OPS', name: 'Cloud Ops', active: false });
  });

  it('activates and deactivates a category through the dedicated endpoints', async () => {
    const api = await loadClient(false);
    const fetchMock = vi.fn(respond({ id: 'NETWORK', name: 'Network', active: true }));
    vi.stubGlobal('fetch', fetchMock);

    await api.adminApi.setCategoryActive('NETWORK', false);
    await api.adminApi.setCategoryActive('NETWORK', true);

    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/categories/NETWORK/deactivate');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
    expect(fetchMock.mock.calls[1][0]).toBe('/api/admin/categories/NETWORK/activate');
  });

  it('preserves 403 and 400 errors from the backend', async () => {
    const api = await loadClient(false);
    vi.stubGlobal('fetch', vi.fn(respond({ code: 'UNAUTHORIZED_OPERATION', message: 'admin only' }, 403)));
    await expect(api.adminApi.listServices()).rejects.toMatchObject({ status: 403, code: 'UNAUTHORIZED_OPERATION' });

    vi.stubGlobal('fetch', vi.fn(respond({ code: 'INVALID_INPUT', message: 'Category id is invalid' }, 400)));
    await expect(api.adminApi.saveCategory({ id: 'bad', name: 'x', active: true }))
      .rejects.toMatchObject({ status: 400, message: 'Category id is invalid' });
  });
});

describe('A6 services/categories admin API in mock mode', () => {
  afterEach(() => vi.unstubAllEnvs());

  it('upserts services by id (no duplicates)', async () => {
    const { adminApi } = await loadClient(true);
    const before = await adminApi.listServices();

    await adminApi.saveService({ id: 'SVC_NEW', name: 'New', businessCriticality: 'LOW' });
    await adminApi.saveService({ id: 'SVC_NEW', name: 'Renamed', businessCriticality: 'HIGH' });

    const after = await adminApi.listServices();
    expect(after).toHaveLength(before.length + 1);
    expect(after.find((s) => s.id === 'SVC_NEW')).toEqual({ id: 'SVC_NEW', name: 'Renamed', businessCriticality: 'HIGH' });
  });

  it('upserts categories and toggles their active flag; unknown id is rejected', async () => {
    const { adminApi } = await loadClient(true);

    await adminApi.saveCategory({ id: 'NEWCAT', name: 'New cat', active: true });
    await adminApi.setCategoryActive('NEWCAT', false);

    const list = await adminApi.listCategories();
    expect(list.find((c) => c.id === 'NEWCAT').active).toBe(false);
    await expect(adminApi.setCategoryActive('NOPE', true)).rejects.toThrow(/Category not found/);
  });
});
