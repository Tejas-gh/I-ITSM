import { INCIDENT_STATUS } from '../constants/itsmConstants';

const STEPS = [
  INCIDENT_STATUS.NEW,
  INCIDENT_STATUS.CLASSIFIED,
  INCIDENT_STATUS.ASSIGNED,
  INCIDENT_STATUS.ACKNOWLEDGED,
  INCIDENT_STATUS.IN_PROGRESS,
  INCIDENT_STATUS.RESOLVED,
  INCIDENT_STATUS.VERIFIED,
  INCIDENT_STATUS.CLOSED,
];

export default function LifecycleStepper({ currentStatus }) {
  const isWaiting = currentStatus === INCIDENT_STATUS.WAITING_FOR_USER;
  const activeIdx = isWaiting
    ? STEPS.indexOf(INCIDENT_STATUS.IN_PROGRESS)
    : STEPS.indexOf(currentStatus);

  return (
    <div style={{ margin: '20px 0', padding: '16px', background: '#f8fafc', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', position: 'relative' }}>
        {STEPS.map((step, idx) => {
          const isDone = idx < activeIdx || currentStatus === INCIDENT_STATUS.CLOSED;
          const isCurrent = idx === activeIdx;

          let stepColor = '#94a3b8';
          let bgColor = '#fff';
          let borderColor = '#cbd5e1';

          if (isDone) {
            stepColor = '#059669';
            bgColor = '#ecfdf5';
            borderColor = '#10b981';
          } else if (isCurrent) {
            stepColor = isWaiting ? '#d97706' : '#2563eb';
            bgColor = isWaiting ? '#fffbeb' : '#eff6ff';
            borderColor = isWaiting ? '#f59e0b' : '#3b82f6';
          }

          return (
            <div
              key={step}
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                flex: 1,
                position: 'relative',
                zIndex: 1,
              }}
            >
              <div
                style={{
                  width: '32px',
                  height: '32px',
                  borderRadius: '50%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  backgroundColor: bgColor,
                  border: `2px solid ${borderColor}`,
                  color: stepColor,
                  fontWeight: '700',
                  fontSize: '13px',
                  boxShadow: isCurrent ? '0 0 0 4px rgba(59, 130, 246, 0.15)' : 'none',
                }}
              >
                {isDone ? '✓' : idx + 1}
              </div>
              <span
                style={{
                  marginTop: '8px',
                  fontSize: '11px',
                  fontWeight: isCurrent ? '700' : '500',
                  color: isCurrent ? stepColor : '#64748b',
                  textAlign: 'center',
                  textTransform: 'capitalize',
                }}
              >
                {step.toLowerCase().replace('_', ' ')}
                {isCurrent && isWaiting && (
                  <span style={{ display: 'block', color: '#b45309', fontWeight: 'bold' }}>
                    (Waiting on User)
                  </span>
                )}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
