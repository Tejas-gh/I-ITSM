import { PRIORITY_LEVEL, PRIORITY_LABELS } from '../constants/itsmConstants';

const PRIORITY_CONFIG = {
  [PRIORITY_LEVEL.P1]: { bg: '#fee2e2', text: '#991b1b', border: '#fca5a5', icon: '🔴' },
  [PRIORITY_LEVEL.P2]: { bg: '#ffedd5', text: '#9a3412', border: '#fdba74', icon: '🟠' },
  [PRIORITY_LEVEL.P3]: { bg: '#e0f2fe', text: '#075985', border: '#7dd3fc', icon: '🔵' },
  [PRIORITY_LEVEL.P4]: { bg: '#f3f4f6', text: '#4b5563', border: '#d1d5db', icon: '⚪' },
};

export default function PriorityBadge({ priority, score, showScore = false }) {
  const config = PRIORITY_CONFIG[priority] || { bg: '#f3f4f6', text: '#4b5563', border: '#d1d5db', icon: '⚪' };
  const label = PRIORITY_LABELS[priority] || priority;

  const style = {
    backgroundColor: config.bg,
    color: config.text,
    border: `1px solid ${config.border}`,
    borderRadius: '6px',
    padding: '3px 8px',
    fontSize: '12px',
    fontWeight: '700',
    display: 'inline-flex',
    alignItems: 'center',
    gap: '5px',
    letterSpacing: '0.3px',
  };

  return (
    <span style={style} title={score ? `Priority Score: ${score}` : undefined}>
      <span>{config.icon}</span>
      <span>{priority} — {label}</span>
      {showScore && score !== undefined && (
        <span style={{ opacity: 0.8, fontWeight: 'normal', fontSize: '11px' }}>
          ({score} pts)
        </span>
      )}
    </span>
  );
}
