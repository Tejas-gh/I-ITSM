import { useState, useEffect } from 'react';
import { adminApi } from '../../api/apiClient';
import { BUSINESS_CRITICALITY } from '../../constants/itsmConstants';
import {
  card, heading, muted, table, th, td, label, input, grid, btnPrimary, btnSecondary, btnSmall, errorText, okText,
} from './adminStyles';

const EMPTY_FORM = { id: '', name: '', businessCriticality: BUSINESS_CRITICALITY.MEDIUM, editingId: null };

/** List IT services and create/edit them (GET/POST /api/admin/services; save is an upsert by id). */
export default function ServicesPanel() {
  const [services, setServices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [form, setForm] = useState(null); // null = hidden
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    async function fetchServices() {
      try {
        const list = await adminApi.listServices();
        if (!cancelled) setServices(list);
      } catch (err) {
        if (!cancelled) setError(err.message || 'Failed to load services');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    fetchServices();
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  const setField = (name) => (e) => setForm((f) => ({ ...f, [name]: e.target.value }));

  function startCreate() {
    setError('');
    setMessage('');
    setForm({ ...EMPTY_FORM });
  }

  function startEdit(s) {
    setError('');
    setMessage('');
    setForm({ id: s.id, name: s.name, businessCriticality: s.businessCriticality, editingId: s.id });
  }

  async function submit(e) {
    e.preventDefault();
    setError('');
    setMessage('');
    const id = form.id.trim();
    const name = form.name.trim();
    if (!id) {
      setError('Service ID is required.');
      return;
    }
    if (!name) {
      setError('Service name is required.');
      return;
    }
    try {
      await adminApi.saveService({ id, name, businessCriticality: form.businessCriticality });
      setMessage(form.editingId ? `Updated ${id}.` : `Created ${id}.`);
      setForm(null);
      setReloadKey((k) => k + 1);
    } catch (err) {
      setError(err.message || 'Failed to save the service');
    }
  }

  const editing = form && form.editingId;

  return (
    <section style={card} aria-label="Services">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={heading}>Services</h2>
        <button type="button" style={btnPrimary} onClick={startCreate}>New service</button>
      </div>
      <p style={muted}>IT services that incidents can be raised against. Business criticality feeds the priority calculation.</p>

      {loading ? (
        <div style={muted}>Loading...</div>
      ) : services.length === 0 ? (
        <div style={muted}>No services defined.</div>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table style={table}>
            <thead>
              <tr>
                <th style={th}>ID</th>
                <th style={th}>Name</th>
                <th style={th}>Business criticality</th>
                <th style={th}></th>
              </tr>
            </thead>
            <tbody>
              {services.map((s) => (
                <tr key={s.id} data-testid="service-row">
                  <td style={td}><strong>{s.id}</strong></td>
                  <td style={td}>{s.name}</td>
                  <td style={td}>{s.businessCriticality}</td>
                  <td style={td}>
                    <button type="button" style={btnSmall} onClick={() => startEdit(s)}>Edit {s.id}</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {form && (
        <form onSubmit={submit} aria-label="Service form" style={{ marginTop: '20px' }}>
          <h3 style={{ ...heading, fontSize: '14px' }}>{editing ? `Edit ${editing}` : 'Create service'}</h3>
          <div style={grid}>
            <div>
              <label htmlFor="svc-id" style={label}>Service ID</label>
              <input id="svc-id" value={form.id} onChange={setField('id')} style={input} disabled={!!editing} />
            </div>
            <div>
              <label htmlFor="svc-name" style={label}>Name</label>
              <input id="svc-name" value={form.name} onChange={setField('name')} style={input} />
            </div>
            <div>
              <label htmlFor="svc-crit" style={label}>Business criticality</label>
              <select id="svc-crit" value={form.businessCriticality} onChange={setField('businessCriticality')} style={input}>
                {Object.values(BUSINESS_CRITICALITY).map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </div>
          </div>
          <div style={{ display: 'flex', gap: '10px', marginTop: '14px' }}>
            <button type="submit" style={btnPrimary}>{editing ? 'Save changes' : 'Create service'}</button>
            <button type="button" style={btnSecondary} onClick={() => setForm(null)}>Cancel</button>
          </div>
        </form>
      )}

      {error && <div role="alert" style={errorText}>{error}</div>}
      {message && <div role="status" style={okText}>{message}</div>}
    </section>
  );
}
