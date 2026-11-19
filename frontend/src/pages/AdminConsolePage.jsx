import { useState } from 'react';
import StrategyPanel from '../components/admin/StrategyPanel';
import SlaPoliciesPanel from '../components/admin/SlaPoliciesPanel';
import UsersPanel from '../components/admin/UsersPanel';
import AuditPanel from '../components/admin/AuditPanel';

const TABS = [
  { id: 'strategy', label: 'Assignment Strategy', Panel: StrategyPanel },
  { id: 'sla', label: 'SLA Policies', Panel: SlaPoliciesPanel },
  { id: 'users', label: 'Users', Panel: UsersPanel },
  { id: 'audit', label: 'Audit', Panel: AuditPanel },
];

/** Admin Console Stage 1: assignment strategy, SLA policies, users and audit. */
export default function AdminConsolePage() {
  const [active, setActive] = useState('strategy');
  const { Panel } = TABS.find((t) => t.id === active);

  return (
    <div style={{ maxWidth: '1100px', margin: '0 auto', padding: '32px 24px' }}>
      <h1 style={{ fontSize: '24px', fontWeight: '800', color: '#0f172a', margin: '0 0 6px 0' }}>Admin Console</h1>
      <p style={{ color: '#64748b', fontSize: '14px', margin: '0 0 20px 0' }}>
        System configuration and oversight for administrators.
      </p>

      <div role="tablist" aria-label="Admin sections" style={{ display: 'flex', gap: '8px', marginBottom: '20px', flexWrap: 'wrap' }}>
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={active === t.id}
            onClick={() => setActive(t.id)}
            style={{
              padding: '8px 16px', borderRadius: '6px', fontSize: '13px', fontWeight: '600', cursor: 'pointer',
              border: active === t.id ? '1px solid #2563eb' : '1px solid #cbd5e1',
              backgroundColor: active === t.id ? '#2563eb' : '#fff',
              color: active === t.id ? '#fff' : '#334155',
            }}
          >
            {t.label}
          </button>
        ))}
      </div>

      <Panel />
    </div>
  );
}
