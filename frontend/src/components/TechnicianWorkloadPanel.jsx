/** Technician capacity overview for administrators (data from GET /api/dashboard/technicians). */
export default function TechnicianWorkloadPanel({ workloads }) {
  return (
    <div style={panelStyle} data-testid="technician-workload">
      <h2 style={{ fontSize: '16px', fontWeight: '700', color: '#0f172a', margin: '0 0 16px 0' }}>
        Technician Workload
      </h2>
      {workloads.length === 0 ? (
        <div style={{ color: '#64748b', fontSize: '13px' }}>No technicians found.</div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {workloads.map((t) => {
            const percent = t.maxActiveIncidents > 0 ? Math.round((t.activeIncidents / t.maxActiveIncidents) * 100) : 0;
            const color = percent >= 100 ? '#dc2626' : percent >= 80 ? '#d97706' : '#16a34a';
            return (
              <div key={t.technicianId} data-testid="workload-row">
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', marginBottom: '4px' }}>
                  <span style={{ fontWeight: '500', color: '#334155' }}>{t.technicianName}</span>
                  <span style={{ fontWeight: '700', color }}>
                    {t.activeIncidents}/{t.maxActiveIncidents}
                  </span>
                </div>
                <div style={{ width: '100%', height: '6px', background: '#e2e8f0', borderRadius: '3px', overflow: 'hidden' }}>
                  <div style={{ width: `${Math.min(percent, 100)}%`, height: '100%', background: color, borderRadius: '3px' }} />
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

const panelStyle = {
  backgroundColor: '#ffffff',
  borderRadius: '12px',
  padding: '20px',
  border: '1px solid #e2e8f0',
  boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.05)',
  height: 'fit-content',
};
