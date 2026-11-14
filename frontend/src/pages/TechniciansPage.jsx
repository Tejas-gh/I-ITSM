import { useState, useEffect } from 'react';
import { referenceApi, incidentApi } from '../api/apiClient';
import { INCIDENT_STATUS } from '../constants/itsmConstants';

export default function TechniciansPage() {
  const [technicians, setTechnicians] = useState([]);
  const [incidents, setIncidents] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadData() {
      try {
        setLoading(true);
        const [techList, incList] = await Promise.all([
          referenceApi.listTechnicians(),
          incidentApi.listVisibleIncidents(null),
        ]);
        setTechnicians(techList);
        setIncidents(incList);
      } catch (e) {
        console.error('Failed loading technicians data', e);
      } finally {
        setLoading(false);
      }
    }
    loadData();
  }, []);

  const activeWorkloadStatuses = [
    INCIDENT_STATUS.ASSIGNED,
    INCIDENT_STATUS.ACKNOWLEDGED,
    INCIDENT_STATUS.IN_PROGRESS,
    INCIDENT_STATUS.WAITING_FOR_USER,
  ];

  return (
    <div style={{ maxWidth: '1100px', margin: '0 auto', padding: '32px 24px' }}>
      <div style={{ marginBottom: '24px' }}>
        <h1 style={{ fontSize: '24px', fontWeight: '800', color: '#0f172a', margin: '0 0 6px 0' }}>
          Support Engineering & Technician Workload
        </h1>
        <p style={{ color: '#64748b', fontSize: '14px', margin: 0 }}>
          Real-time active incident allocation, capacity limits, and skill sets.
        </p>
      </div>

      {loading ? (
        <div style={{ padding: '60px', textAlign: 'center', color: '#64748b' }}>Loading technician metrics...</div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '20px' }}>
          {technicians.map((tech) => {
            const activeIncidents = incidents.filter(
              (i) => i.assignedTechnicianId === tech.id && activeWorkloadStatuses.includes(i.status)
            );
            const activeCount = activeIncidents.length;
            const maxCount = tech.maxActiveIncidents || 5;
            const utilization = Math.min(100, Math.round((activeCount / maxCount) * 100));
            const isOverloaded = activeCount >= maxCount;

            return (
              <div
                key={tech.id}
                style={{
                  backgroundColor: '#ffffff',
                  borderRadius: '12px',
                  padding: '24px',
                  border: isOverloaded ? '2px solid #fca5a5' : '1px solid #e2e8f0',
                  boxShadow: '0 1px 3px rgba(0, 0, 0, 0.05)',
                }}
              >
                {/* Header */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '14px' }}>
                  <div>
                    <h3 style={{ margin: 0, fontSize: '18px', fontWeight: '700', color: '#0f172a' }}>
                      {tech.name}
                    </h3>
                    <div style={{ fontSize: '12px', color: '#64748b', marginTop: '2px' }}>
                      {tech.id} • {tech.role} • {tech.yearsOfExperience || 5} yrs experience
                    </div>
                  </div>
                  <span
                    style={{
                      padding: '3px 8px',
                      borderRadius: '12px',
                      fontSize: '12px',
                      fontWeight: '600',
                      backgroundColor: tech.available ? '#dcfce7' : '#fee2e2',
                      color: tech.available ? '#166534' : '#991b1b',
                    }}
                  >
                    {tech.available ? '● Available' : '○ Unavailable'}
                  </span>
                </div>

                {/* Capacity Bar */}
                <div style={{ marginBottom: '16px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', marginBottom: '6px' }}>
                    <span style={{ color: '#475569', fontWeight: '500' }}>Active Workload:</span>
                    <span style={{ fontWeight: '700', color: isOverloaded ? '#dc2626' : '#0f172a' }}>
                      {activeCount} / {maxCount} Max Incidents {isOverloaded && '(Overloaded)'}
                    </span>
                  </div>
                  <div style={{ width: '100%', height: '8px', background: '#e2e8f0', borderRadius: '4px', overflow: 'hidden' }}>
                    <div
                      style={{
                        width: `${utilization}%`,
                        height: '100%',
                        backgroundColor: isOverloaded ? '#ef4444' : utilization > 60 ? '#f59e0b' : '#3b82f6',
                        borderRadius: '4px',
                      }}
                    />
                  </div>
                </div>

                {/* Technical Skills */}
                <div>
                  <div style={{ fontSize: '12px', fontWeight: '700', color: '#64748b', textTransform: 'uppercase', marginBottom: '8px' }}>
                    Technical Skill Matrix
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                    {Object.entries(tech.skillLevels || {}).map(([skill, level]) => (
                      <span
                        key={skill}
                        style={{
                          padding: '4px 8px',
                          borderRadius: '6px',
                          backgroundColor: '#f1f5f9',
                          border: '1px solid #e2e8f0',
                          fontSize: '12px',
                          color: '#334155',
                          fontWeight: '500',
                        }}
                      >
                        {skill}: <strong>Lvl {level}</strong>
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
