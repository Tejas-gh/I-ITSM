import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { incidentApi, referenceApi } from '../api/apiClient';
import { useAuth } from '../context/AuthContext';
import StatusBadge from '../components/StatusBadge';
import PriorityBadge from '../components/PriorityBadge';
import SlaBadge from '../components/SlaBadge';
import { INCIDENT_STATUS, PRIORITY_LEVEL } from '../constants/itsmConstants';

export default function DashboardPage() {
  const { currentUser } = useAuth();
  const [incidents, setIncidents] = useState([]);
  const [services, setServices] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadData() {
      try {
        setLoading(true);
        const [incList, srvList] = await Promise.all([
          incidentApi.listVisibleIncidents(currentUser),
          referenceApi.listServices(),
        ]);
        setIncidents(incList);
        setServices(srvList);
      } catch (err) {
        console.error('Failed to load dashboard data', err);
      } finally {
        setLoading(false);
      }
    }
    loadData();
  }, [currentUser]);

  if (loading) {
    return (
      <div style={{ padding: '60px', textAlign: 'center', color: '#64748b' }}>
        Loading dashboard metrics...
      </div>
    );
  }

  // Calculate high-level metrics
  const total = incidents.length;
  const openCount = incidents.filter(
    (i) => i.status !== INCIDENT_STATUS.RESOLVED && i.status !== INCIDENT_STATUS.VERIFIED && i.status !== INCIDENT_STATUS.CLOSED
  ).length;
  const criticalCount = incidents.filter((i) => i.priority === PRIORITY_LEVEL.P1).length;
  const resolvedCount = incidents.filter(
    (i) => i.status === INCIDENT_STATUS.RESOLVED || i.status === INCIDENT_STATUS.VERIFIED || i.status === INCIDENT_STATUS.CLOSED
  ).length;

  const now = new Date();
  const slaBreaches = incidents.filter((i) => {
    if (i.status === INCIDENT_STATUS.CLOSED) return false;
    const resDeadline = i.resolutionDeadline ? new Date(i.resolutionDeadline) : null;
    return resDeadline && now > resDeadline && !i.resolvedAt;
  }).length;

  // Workload / Service Breakdown
  const serviceCounts = {};
  incidents.forEach((inc) => {
    serviceCounts[inc.serviceId] = (serviceCounts[inc.serviceId] || 0) + 1;
  });

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', padding: '32px 24px' }}>
      {/* Welcome banner */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '28px' }}>
        <div>
          <h1 style={{ fontSize: '26px', fontWeight: '800', color: '#0f172a', margin: '0 0 6px 0' }}>
            ITSM Service Desk Overview
          </h1>
          <p style={{ color: '#64748b', fontSize: '14px', margin: 0 }}>
            Viewing perspective for <strong style={{ color: '#0f172a' }}>{currentUser.name}</strong> ({currentUser.role})
          </p>
        </div>
        <Link
          to="/incidents/new"
          style={{
            backgroundColor: '#2563eb',
            color: '#fff',
            padding: '10px 20px',
            borderRadius: '8px',
            fontWeight: '600',
            fontSize: '14px',
            textDecoration: 'none',
            display: 'inline-flex',
            alignItems: 'center',
            gap: '8px',
            boxShadow: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
          }}
        >
          <span>+</span> Report New Incident
        </Link>
      </div>

      {/* KPI Metric Cards */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
          gap: '16px',
          marginBottom: '32px',
        }}
      >
        <div style={cardStyle}>
          <div style={metricLabelStyle}>Total Incidents</div>
          <div style={{ ...metricValueStyle, color: '#0f172a' }}>{total}</div>
          <div style={metricSubStyle}>Visible in role scope</div>
        </div>

        <div style={cardStyle}>
          <div style={metricLabelStyle}>Open & Active</div>
          <div style={{ ...metricValueStyle, color: '#2563eb' }}>{openCount}</div>
          <div style={metricSubStyle}>Require resolution</div>
        </div>

        <div style={cardStyle}>
          <div style={metricLabelStyle}>P1 Critical</div>
          <div style={{ ...metricValueStyle, color: '#dc2626' }}>{criticalCount}</div>
          <div style={metricSubStyle}>Immediate escalation</div>
        </div>

        <div style={cardStyle}>
          <div style={metricLabelStyle}>Resolved & Closed</div>
          <div style={{ ...metricValueStyle, color: '#16a34a' }}>{resolvedCount}</div>
          <div style={metricSubStyle}>Completed resolution</div>
        </div>

        <div style={cardStyle}>
          <div style={metricLabelStyle}>Active SLA Breaches</div>
          <div style={{ ...metricValueStyle, color: slaBreaches > 0 ? '#dc2626' : '#16a34a' }}>
            {slaBreaches}
          </div>
          <div style={metricSubStyle}>Resolution past target</div>
        </div>
      </div>

      {/* Two columns: Service breakdown & Recent Incidents */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '24px' }}>
        {/* Service Analysis */}
        <div style={{ ...cardStyle, height: 'fit-content' }}>
          <h2 style={{ fontSize: '16px', fontWeight: '700', color: '#0f172a', margin: '0 0 16px 0' }}>
            Service Incident Distribution
          </h2>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {services.map((srv) => {
              const count = serviceCounts[srv.id] || 0;
              const percent = total > 0 ? Math.round((count / total) * 100) : 0;
              return (
                <div key={srv.id}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', marginBottom: '4px' }}>
                    <span style={{ fontWeight: '500', color: '#334155' }}>{srv.name}</span>
                    <span style={{ fontWeight: '700', color: '#64748b' }}>{count}</span>
                  </div>
                  <div style={{ width: '100%', height: '6px', background: '#e2e8f0', borderRadius: '3px', overflow: 'hidden' }}>
                    <div
                      style={{
                        width: `${percent}%`,
                        height: '100%',
                        background: '#3b82f6',
                        borderRadius: '3px',
                      }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Recent Incidents Table */}
        <div style={cardStyle}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <h2 style={{ fontSize: '16px', fontWeight: '700', color: '#0f172a', margin: 0 }}>
              Recent Incidents
            </h2>
            <Link to="/incidents" style={{ fontSize: '13px', fontWeight: '600', color: '#2563eb', textDecoration: 'none' }}>
              View all incidents →
            </Link>
          </div>

          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid #e2e8f0', color: '#64748b' }}>
                  <th style={{ padding: '8px 12px' }}>ID</th>
                  <th style={{ padding: '8px 12px' }}>Title</th>
                  <th style={{ padding: '8px 12px' }}>Priority</th>
                  <th style={{ padding: '8px 12px' }}>Status</th>
                  <th style={{ padding: '8px 12px' }}>Resolution SLA</th>
                </tr>
              </thead>
              <tbody>
                {incidents.slice(0, 5).map((inc) => (
                  <tr
                    key={inc.id}
                    style={{
                      borderBottom: '1px solid #f1f5f9',
                      transition: 'background 0.15s',
                    }}
                  >
                    <td style={{ padding: '12px', fontWeight: '700' }}>
                      <Link to={`/incidents/${inc.id}`} style={{ color: '#2563eb', textDecoration: 'none' }}>
                        {inc.id}
                      </Link>
                    </td>
                    <td style={{ padding: '12px' }}>
                      <Link
                        to={`/incidents/${inc.id}`}
                        style={{ color: '#0f172a', textDecoration: 'none', fontWeight: '600' }}
                      >
                        {inc.title}
                      </Link>
                    </td>
                    <td style={{ padding: '12px' }}>
                      <PriorityBadge priority={inc.priority} />
                    </td>
                    <td style={{ padding: '12px' }}>
                      <StatusBadge status={inc.status} />
                    </td>
                    <td style={{ padding: '12px' }}>
                      <SlaBadge deadline={inc.resolutionDeadline} doneAt={inc.resolvedAt} label="Resolution" />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}

const cardStyle = {
  backgroundColor: '#ffffff',
  borderRadius: '12px',
  padding: '20px',
  border: '1px solid #e2e8f0',
  boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.05)',
};

const metricLabelStyle = {
  fontSize: '12px',
  fontWeight: '600',
  color: '#64748b',
  textTransform: 'uppercase',
  letterSpacing: '0.5px',
};

const metricValueStyle = {
  fontSize: '32px',
  fontWeight: '800',
  margin: '8px 0 4px',
};

const metricSubStyle = {
  fontSize: '12px',
  color: '#94a3b8',
};
