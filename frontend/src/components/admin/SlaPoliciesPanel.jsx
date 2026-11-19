import { useState, useEffect } from 'react';
import { adminApi, referenceApi, parseDurationSeconds, formatDuration } from '../../api/apiClient';
import { PRIORITY_LEVEL } from '../../constants/itsmConstants';
import {
  card, heading, muted, table, th, td, label, input, grid, btnPrimary, btnSecondary, btnSmall, errorText, okText,
} from './adminStyles';

const EMPTY_FORM = { serviceId: '', priority: 'P1', responseMinutes: '', resolutionMinutes: '' };

/** View SLA policies and add/edit them (GET/POST /api/admin/sla-policies; save is an upsert per service+priority). */
export default function SlaPoliciesPanel() {
  const [policies, setPolicies] = useState([]);
  const [services, setServices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [form, setForm] = useState(EMPTY_FORM);

  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    async function fetchPolicies() {
      try {
        const [list, svc] = await Promise.all([adminApi.listSlaPolicies(), referenceApi.listServices()]);
        if (cancelled) return;
        setPolicies(list);
        setServices(svc);
      } catch (err) {
        if (!cancelled) setError(err.message || 'Failed to load SLA policies');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    fetchPolicies();
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  const serviceName = (id) => (id ? services.find((s) => s.id === id)?.name || id : 'Default (all services)');
  const setField = (name) => (e) => setForm((f) => ({ ...f, [name]: e.target.value }));

  function edit(policy) {
    setMessage('');
    setError('');
    setForm({
      serviceId: policy.serviceId || '',
      priority: policy.priority,
      responseMinutes: String(parseDurationSeconds(policy.responseTarget) / 60),
      resolutionMinutes: String(parseDurationSeconds(policy.resolutionTarget) / 60),
    });
  }

  async function submit(e) {
    e.preventDefault();
    setError('');
    setMessage('');
    const response = Number(form.responseMinutes);
    const resolution = Number(form.resolutionMinutes);
    if (!(response > 0) || !(resolution > 0)) {
      setError('Response and resolution targets must be positive numbers of minutes.');
      return;
    }
    if (response > resolution) {
      setError('The response target must not exceed the resolution target.');
      return;
    }
    try {
      await adminApi.saveSlaPolicy({
        serviceId: form.serviceId,
        priority: form.priority,
        responseMinutes: response,
        resolutionMinutes: resolution,
      });
      setMessage(`Saved ${form.priority} policy for ${serviceName(form.serviceId)}.`);
      setForm(EMPTY_FORM);
      setReloadKey((k) => k + 1);
    } catch (err) {
      setError(err.message || 'Failed to save the SLA policy');
    }
  }

  return (
    <section style={card} aria-label="SLA policies">
      <h2 style={heading}>SLA Policies</h2>
      <p style={muted}>Response and resolution targets per priority. A per-service policy overrides the default.</p>

      {loading ? (
        <div style={muted}>Loading...</div>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table style={table}>
            <thead>
              <tr>
                <th style={th}>Scope</th>
                <th style={th}>Priority</th>
                <th style={th}>Response target</th>
                <th style={th}>Resolution target</th>
                <th style={th}></th>
              </tr>
            </thead>
            <tbody>
              {policies.map((p) => (
                <tr key={`${p.serviceId || '*'}|${p.priority}`} data-testid="sla-policy-row">
                  <td style={td}>{serviceName(p.serviceId)}</td>
                  <td style={td}>{p.priority}</td>
                  <td style={td}>{formatDuration(parseDurationSeconds(p.responseTarget))}</td>
                  <td style={td}>{formatDuration(parseDurationSeconds(p.resolutionTarget))}</td>
                  <td style={td}>
                    <button type="button" style={btnSmall} onClick={() => edit(p)}
                      aria-label={`Edit ${p.priority} policy for ${serviceName(p.serviceId)}`}>
                      Edit
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <form onSubmit={submit} aria-label="SLA policy form" style={{ marginTop: '20px' }}>
        <h3 style={{ ...heading, fontSize: '14px' }}>Add or update a policy</h3>
        <div style={grid}>
          <div>
            <label htmlFor="sla-service" style={label}>Service</label>
            <select id="sla-service" value={form.serviceId} onChange={setField('serviceId')} style={input}>
              <option value="">Default (all services)</option>
              {services.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="sla-priority" style={label}>Priority</label>
            <select id="sla-priority" value={form.priority} onChange={setField('priority')} style={input}>
              {Object.values(PRIORITY_LEVEL).map((p) => (
                <option key={p} value={p}>{p}</option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="sla-response" style={label}>Response (minutes)</label>
            <input id="sla-response" type="number" min="0" step="any" value={form.responseMinutes}
              onChange={setField('responseMinutes')} style={input} />
          </div>
          <div>
            <label htmlFor="sla-resolution" style={label}>Resolution (minutes)</label>
            <input id="sla-resolution" type="number" min="0" step="any" value={form.resolutionMinutes}
              onChange={setField('resolutionMinutes')} style={input} />
          </div>
        </div>
        <div style={{ display: 'flex', gap: '10px', marginTop: '14px' }}>
          <button type="submit" style={btnPrimary}>Save policy</button>
          <button type="button" style={btnSecondary} onClick={() => setForm(EMPTY_FORM)}>Clear</button>
        </div>
      </form>
      {error && <div role="alert" style={errorText}>{error}</div>}
      {message && <div role="status" style={okText}>{message}</div>}
    </section>
  );
}
