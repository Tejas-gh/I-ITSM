import { useState, useEffect } from 'react';
import { adminApi } from '../../api/apiClient';
import { card, heading, muted, errorText, okText, btnPrimary } from './adminStyles';

/** View and switch the technician-assignment strategy (GET/PUT /api/admin/assignment-strategy). */
export default function StrategyPanel() {
  const [selection, setSelection] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const result = await adminApi.getAssignmentStrategy();
        if (!cancelled) setSelection(result);
      } catch (err) {
        if (!cancelled) setError(err.message || 'Failed to load the assignment strategy');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, []);

  async function activate(name) {
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const result = await adminApi.selectAssignmentStrategy(name);
      setSelection(result);
      setMessage(`Assignment strategy switched to ${result.selected}.`);
    } catch (err) {
      setError(err.message || 'Failed to switch the assignment strategy');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section style={card} aria-label="Assignment strategy">
      <h2 style={heading}>Assignment Strategy</h2>
      <p style={muted}>The strategy used for automatic technician assignment. A switch applies to the next assignment.</p>
      {loading ? (
        <div style={muted}>Loading...</div>
      ) : (
        <>
          {selection && (
            <>
              <p style={{ fontSize: '14px' }}>
                Current strategy: <strong data-testid="current-strategy">{selection.selected}</strong>
              </p>
              <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
                {selection.available.map((name) => {
                  const active = name === selection.selected;
                  return (
                    <div
                      key={name}
                      data-testid={`strategy-${name}`}
                      style={{
                        border: active ? '2px solid #2563eb' : '1px solid #e2e8f0',
                        backgroundColor: active ? '#eff6ff' : '#fff',
                        borderRadius: '8px', padding: '14px 18px', minWidth: '160px',
                      }}
                    >
                      <div style={{ fontWeight: '700', marginBottom: '8px' }}>{name}</div>
                      {active ? (
                        <span style={{ color: '#1d4ed8', fontSize: '12px', fontWeight: '700' }}>ACTIVE</span>
                      ) : (
                        <button type="button" style={btnPrimary} disabled={busy} onClick={() => activate(name)}>
                          Activate {name}
                        </button>
                      )}
                    </div>
                  );
                })}
              </div>
            </>
          )}
          {error && <div role="alert" style={errorText}>{error}</div>}
          {message && <div role="status" style={okText}>{message}</div>}
        </>
      )}
    </section>
  );
}
