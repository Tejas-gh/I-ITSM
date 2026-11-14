import { INCIDENT_STATUS } from '../constants/itsmConstants';

const STATUS_CONFIG = {
  [INCIDENT_STATUS.NEW]: { bg: '#e0e7ff', text: '#3730a3', border: '#c7d2fe', label: 'New' },
  [INCIDENT_STATUS.CLASSIFIED]: { bg: '#ede9fe', text: '#5b21b6', border: '#ddd6fe', label: 'Classified' },
  [INCIDENT_STATUS.ASSIGNED]: { bg: '#dbeafe', text: '#1e40af', border: '#bfdbfe', label: 'Assigned' },
  [INCIDENT_STATUS.ACKNOWLEDGED]: { bg: '#e0f2fe', text: '#0369a1', border: '#bae6fd', label: 'Acknowledged' },
  [INCIDENT_STATUS.IN_PROGRESS]: { bg: '#ccfbf1', text: '#0f766e', border: '#99f6e4', label: 'In Progress' },
  [INCIDENT_STATUS.WAITING_FOR_USER]: { bg: '#fef3c7', text: '#92400e', border: '#fde68a', label: 'Waiting for User' },
  [INCIDENT_STATUS.RESOLVED]: { bg: '#dcfce7', text: '#166534', border: '#bbf7d0', label: 'Resolved' },
  [INCIDENT_STATUS.VERIFIED]: { bg: '#ecfdf5', text: '#065f46', border: '#a7f3d0', label: 'Verified' },
  [INCIDENT_STATUS.CLOSED]: { bg: '#f3f4f6', text: '#4b5563', border: '#e5e7eb', label: 'Closed' },
};

export default function StatusBadge({ status, size = 'normal' }) {
  const config = STATUS_CONFIG[status] || { bg: '#f3f4f6', text: '#4b5563', border: '#e5e7eb', label: status };

  const style = {
    backgroundColor: config.bg,
    color: config.text,
    border: `1px solid ${config.border}`,
    borderRadius: '16px',
    padding: size === 'small' ? '2px 8px' : '4px 12px',
    fontSize: size === 'small' ? '12px' : '13px',
    fontWeight: '600',
    display: 'inline-flex',
    alignItems: 'center',
    gap: '6px',
    whiteSpace: 'nowrap',
    letterSpacing: '0.2px',
  };

  const dotStyle = {
    width: '6px',
    height: '6px',
    borderRadius: '50%',
    backgroundColor: config.text,
  };

  return (
    <span style={style}>
      <span style={dotStyle} />
      {config.label}
    </span>
  );
}
