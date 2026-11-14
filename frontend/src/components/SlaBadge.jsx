import { SLA_STATUS } from '../constants/itsmConstants';

export default function SlaBadge({ deadline, doneAt, label = 'SLA' }) {
  if (!deadline) {
    return <span style={{ color: '#9ca3af', fontSize: '12px' }}>—</span>;
  }

  const deadlineDate = new Date(deadline);
  const isDone = Boolean(doneAt);
  const now = new Date();

  let status = SLA_STATUS.ON_TRACK;
  let text;
  let color;
  let bg;
  let border;

  if (isDone) {
    const doneDate = new Date(doneAt);
    if (doneDate <= deadlineDate) {
      status = SLA_STATUS.MET;
      text = 'Met';
      color = '#15803d';
      bg = '#dcfce7';
      border = '#bbf7d0';
    } else {
      status = SLA_STATUS.BREACHED;
      text = 'Breached';
      color = '#b91c1c';
      bg = '#fee2e2';
      border = '#fca5a5';
    }
  } else {
    const diffMs = deadlineDate.getTime() - now.getTime();
    const diffMin = Math.round(diffMs / 60000);

    if (diffMs < 0) {
      status = SLA_STATUS.BREACHED;
      text = `Overdue by ${Math.abs(diffMin)}m`;
      color = '#b91c1c';
      bg = '#fee2e2';
      border = '#fca5a5';
    } else if (diffMin <= 10) {
      status = SLA_STATUS.WARNING;
      text = `${diffMin}m left (Warning)`;
      color = '#b45309';
      bg = '#fef3c7';
      border = '#fde68a';
    } else if (diffMin >= 60) {
      const hours = (diffMin / 60).toFixed(1);
      text = `${hours}h remaining`;
      color = '#0369a1';
      bg = '#e0f2fe';
      border = '#bae6fd';
    } else {
      text = `${diffMin}m remaining`;
      color = '#0f766e';
      bg = '#ccfbf1';
      border = '#99f6e4';
    }
  }

  return (
    <div style={{ display: 'inline-flex', flexDirection: 'column', gap: '2px' }}>
      <span style={{ fontSize: '11px', color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
        {label}
      </span>
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '6px',
          padding: '3px 8px',
          fontSize: '12px',
          fontWeight: '600',
          borderRadius: '4px',
          backgroundColor: bg,
          color,
          border: `1px solid ${border}`,
        }}
      >
        <span>{status === SLA_STATUS.BREACHED ? '⚠️' : status === SLA_STATUS.MET ? '✓' : '⏱️'}</span>
        {text}
      </span>
    </div>
  );
}
