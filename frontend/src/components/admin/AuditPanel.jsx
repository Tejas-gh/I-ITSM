import { useState, useEffect } from 'react';
import { auditApi } from '../../api/apiClient';
import StatusBadge from '../StatusBadge';
import { card, heading, muted, table, th, td, label, input, btnPrimary, btnSecondary, errorText } from './adminStyles';

/** Admin audit view: all entries (GET /api/audit), or one incident's entries searched server-side. */
export default function AuditPanel() {
  const [entries, setEntries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [incidentId, setIncidentId] = useState('');
  // Criteria last submitted; a new object per Search/Reset so every submit re-queries the backend
  const [applied, setApplied] = useState({ id: '' });

  useEffect(() => {
    let cancelled = false;
    async function fetchEntries() {
      setLoading(true);
      setError('');
      try {
        const list = applied.id ? await auditApi.listForIncident(applied.id) : await auditApi.listAll();
        if (cancelled) return;
        // Newest first
        setEntries([...(list || [])].sort((a, b) => new Date(b.occurredAt) - new Date(a.occurredAt)));
      } catch (err) {
        if (!cancelled) {
          setEntries([]);
          setError(err.message || 'Failed to load audit entries');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    fetchEntries();
    return () => {
      cancelled = true;
    };
  }, [applied]);

  function search(e) {
    e.preventDefault();
    setApplied({ id: incidentId.trim() });
  }

  function reset() {
    setIncidentId('');
    setApplied({ id: '' });
  }

  return (
    <section style={card} aria-label="Audit log">
      <h2 style={heading}>Audit Log</h2>
      <form onSubmit={search} aria-label="Audit search" style={{ display: 'flex', gap: '10px', alignItems: 'flex-end', marginBottom: '16px' }}>
        <div style={{ minWidth: '220px' }}>
          <label htmlFor="audit-incident" style={label}>Incident ID</label>
          <input id="audit-incident" value={incidentId} onChange={(e) => setIncidentId(e.target.value)}
            placeholder="e.g. INC1001" style={input} />
        </div>
        <button type="submit" style={btnPrimary}>Search</button>
        <button type="button" style={btnSecondary} onClick={reset}>Reset</button>
      </form>

      {loading ? (
        <div style={muted}>Loading...</div>
      ) : error ? (
        <div role="alert" style={errorText}>{error}</div>
      ) : entries.length === 0 ? (
        <div style={muted}>No audit entries found.</div>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table style={table}>
            <thead>
              <tr>
                <th style={th}>Timestamp</th>
                <th style={th}>Actor</th>
                <th style={th}>Action</th>
                <th style={th}>Incident</th>
                <th style={th}>Previous</th>
                <th style={th}>New</th>
                <th style={th}>Description</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((e, i) => (
                <tr key={`${e.occurredAt}-${e.incidentId}-${e.action}-${i}`} data-testid="audit-row">
                  <td style={td}>{new Date(e.occurredAt).toLocaleString()}</td>
                  <td style={td}>{e.actorId}</td>
                  <td style={td}>{e.action}</td>
                  <td style={td}>{e.incidentId}</td>
                  <td style={td}>{e.previousStatus ? <StatusBadge status={e.previousStatus} size="small" /> : '-'}</td>
                  <td style={td}>{e.newStatus ? <StatusBadge status={e.newStatus} size="small" /> : '-'}</td>
                  <td style={td}>{e.description}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
