import { useState, useEffect } from 'react';
import { auditApi } from '../api/apiClient';
import StatusBadge from './StatusBadge';

/** Chronological audit history of one incident, loaded from the backend (GET /api/audit/incidents/{id}). */
export default function AuditTimeline({ incidentId, refreshKey = '' }) {
  const [entries, setEntries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError('');
      try {
        const list = await auditApi.listForIncident(incidentId);
        if (cancelled) return;
        // Oldest first; Array.sort is stable so same-timestamp entries keep the backend order.
        const sorted = [...(list || [])].sort((a, b) => new Date(a.occurredAt) - new Date(b.occurredAt));
        setEntries(sorted);
      } catch (err) {
        if (!cancelled) setError(err.message || 'Failed to load audit history');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [incidentId, refreshKey]);

  return (
    <div style={cardStyle} data-testid="audit-timeline">
      <h3 style={{ margin: '0 0 16px 0', fontSize: '16px', fontWeight: '700', color: '#0f172a' }}>
        Audit History
      </h3>

      {loading ? (
        <div style={mutedStyle}>Loading audit history...</div>
      ) : error ? (
        <div role="alert" style={{ color: '#b91c1c', fontSize: '13px' }}>{error}</div>
      ) : entries.length === 0 ? (
        <div style={mutedStyle}>No audit entries recorded for this incident.</div>
      ) : (
        <ol style={{ listStyle: 'none', margin: 0, padding: 0 }}>
          {entries.map((e, i) => (
            <li
              key={`${e.occurredAt}-${e.action}-${i}`}
              data-testid="audit-entry"
              style={{ display: 'flex', gap: '14px', paddingBottom: '16px' }}
            >
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                <span style={dotStyle} />
                {i < entries.length - 1 && <span style={lineStyle} />}
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: '8px' }}>
                  <span style={{ fontWeight: '700', fontSize: '13px', color: '#1e293b' }}>
                    {e.action.replace(/_/g, ' ')}
                  </span>
                  <span style={{ fontSize: '12px', color: '#94a3b8' }}>
                    {new Date(e.occurredAt).toLocaleString()}
                  </span>
                </div>
                <div style={{ fontSize: '12px', color: '#64748b', marginTop: '2px' }}>
                  by <strong>{e.actorId}</strong> on {e.incidentId}
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginTop: '6px', fontSize: '12px', color: '#64748b' }}>
                  {e.previousStatus ? <StatusBadge status={e.previousStatus} size="small" /> : <span>(none)</span>}
                  <span aria-hidden="true">→</span>
                  <StatusBadge status={e.newStatus} size="small" />
                </div>
                <p style={{ margin: '6px 0 0 0', fontSize: '13px', color: '#334155' }}>{e.description}</p>
              </div>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

const cardStyle = {
  margin: '24px 0 0',
  backgroundColor: '#fff',
  borderRadius: '10px',
  border: '1px solid #e2e8f0',
  padding: '20px 24px',
};
const mutedStyle = { color: '#64748b', fontSize: '13px' };
const dotStyle = { width: '10px', height: '10px', borderRadius: '50%', backgroundColor: '#2563eb', marginTop: '4px' };
const lineStyle = { flex: 1, width: '2px', backgroundColor: '#e2e8f0', marginTop: '4px' };
