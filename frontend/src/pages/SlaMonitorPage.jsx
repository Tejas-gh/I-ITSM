import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { slaApi, incidentApi } from '../api/apiClient';
import { useAuth } from '../context/AuthContext';
import SlaBadge from '../components/SlaBadge';
import StatusBadge from '../components/StatusBadge';
import PriorityBadge from '../components/PriorityBadge';
import { INCIDENT_STATUS } from '../constants/itsmConstants';

export default function SlaMonitorPage() {
  const { isAdmin, currentUser } = useAuth();

  const [activeTab, setActiveTab] = useState('at-risk'); // 'at-risk' | 'violations' | 'all'
  const [warnings, setWarnings] = useState([]);
  const [violations, setViolations] = useState([]);
  const [openIncidents, setOpenIncidents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [runResult, setRunResult] = useState(null);
  const [filterIncidentId, setFilterIncidentId] = useState('');

  const refreshData = async () => {
    try {
      setLoading(true);
      const [warns, viols, incs] = await Promise.all([
        slaApi.getAtRisk(),
        slaApi.getViolations(),
        incidentApi.listVisibleIncidents(currentUser),
      ]);
      setWarnings(warns || []);
      setViolations(viols || []);
      setOpenIncidents((incs || []).filter((i) => i.status !== INCIDENT_STATUS.CLOSED));
    } catch (err) {
      console.error('Failed loading SLA monitor data', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let ignore = false;
    async function fetchData() {
      try {
        const [warns, viols, incs] = await Promise.all([
          slaApi.getAtRisk(),
          slaApi.getViolations(),
          incidentApi.listVisibleIncidents(currentUser),
        ]);
        if (!ignore) {
          setWarnings(warns || []);
          setViolations(viols || []);
          setOpenIncidents((incs || []).filter((i) => i.status !== INCIDENT_STATUS.CLOSED));
        }
      } catch (err) {
        console.error('Failed loading SLA monitor data', err);
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    }
    fetchData();
    return () => {
      ignore = true;
    };
  }, [currentUser]);

  const handleManualRun = async () => {
    try {
      setRunning(true);
      setRunResult(null);
      const res = await slaApi.runMonitor();
      setRunResult(res);
      await refreshData();
    } catch (err) {
      alert('Failed to run SLA monitor: ' + err.message);
    } finally {
      setRunning(false);
    }
  };

  const incidentMap = {};
  openIncidents.forEach((i) => {
    incidentMap[i.id] = i;
  });

  const totalOpen = openIncidents.length;
  const violationCount = violations.length;
  const warningCount = warnings.length;
  const complianceRate = totalOpen > 0 ? Math.max(0, ((totalOpen - violationCount) / totalOpen) * 100).toFixed(1) : '100.0';

  const filteredWarnings = warnings.filter((w) =>
    filterIncidentId ? w.incidentId?.toLowerCase().includes(filterIncidentId.toLowerCase()) : true
  );

  const filteredViolations = violations.filter((v) =>
    filterIncidentId ? v.incidentId?.toLowerCase().includes(filterIncidentId.toLowerCase()) : true
  );

  const filteredOpen = openIncidents.filter((i) =>
    filterIncidentId ? i.id?.toLowerCase().includes(filterIncidentId.toLowerCase()) : true
  );

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', padding: '32px 24px' }}>
      {/* Page Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '26px', fontWeight: '800', color: '#0f172a', margin: '0 0 6px 0' }}>
            ⏱️ SLA Monitor & Compliance Center
          </h1>
          <p style={{ color: '#64748b', fontSize: '14px', margin: 0 }}>
            Real-time surveillance of response and resolution targets, warning thresholds, and SLA breaches.
          </p>
        </div>

        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          <button
            onClick={refreshData}
            disabled={loading}
            style={{
              padding: '9px 16px',
              borderRadius: '8px',
              border: '1px solid #cbd5e1',
              backgroundColor: '#fff',
              fontSize: '13px',
              fontWeight: '600',
              color: '#475569',
              cursor: loading ? 'not-allowed' : 'pointer',
            }}
          >
            ↻ Refresh
          </button>
          {isAdmin && (
            <button
              onClick={handleManualRun}
              disabled={running}
              data-testid="run-sla-monitor-btn"
              style={{
                backgroundColor: '#2563eb',
                color: '#fff',
                padding: '9px 18px',
                borderRadius: '8px',
                fontWeight: '600',
                fontSize: '13px',
                border: 'none',
                cursor: running ? 'not-allowed' : 'pointer',
                opacity: running ? 0.7 : 1,
                boxShadow: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
              }}
            >
              <span>⚡</span> {running ? 'Evaluating SLAs...' : 'Run SLA Monitor Now'}
            </button>
          )}
        </div>
      </div>

      {/* Manual Run Notification Banner */}
      {runResult && (
        <div
          data-testid="run-result-banner"
          style={{
            padding: '12px 16px',
            backgroundColor: '#ecfdf5',
            border: '1px solid #a7f3d0',
            borderRadius: '8px',
            color: '#065f46',
            fontSize: '13px',
            marginBottom: '24px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <div>
            <strong>✓ SLA Monitor Completed:</strong> Scanned {runResult.incidentsEvaluated ?? runResult.scannedCount ?? 0} open incident(s) • Raised {runResult.warningsGenerated ?? runResult.warningsRaised ?? 0} new warning(s) • Detected {runResult.violationsGenerated ?? runResult.violationsRaised ?? 0} new breach(es) • {runResult.errors ?? runResult.errorCount ?? 0} error(s).
          </div>
          <button
            onClick={() => setRunResult(null)}
            style={{ background: 'transparent', border: 'none', color: '#065f46', cursor: 'pointer', fontSize: '14px' }}
          >
            ✕
          </button>
        </div>
      )}

      {/* Key Metric Cards */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: '16px',
          marginBottom: '28px',
        }}
      >
        <div style={kpiCardStyle}>
          <div style={{ fontSize: '12px', fontWeight: '700', color: '#64748b', textTransform: 'uppercase', marginBottom: '8px' }}>
            At-Risk Incidents (≤ 20% SLA)
          </div>
          <div style={{ fontSize: '28px', fontWeight: '800', color: warningCount > 0 ? '#d97706' : '#0f172a' }}>
            {warningCount}
          </div>
          <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>Active warning alerts</div>
        </div>

        <div style={kpiCardStyle}>
          <div style={{ fontSize: '12px', fontWeight: '700', color: '#64748b', textTransform: 'uppercase', marginBottom: '8px' }}>
            SLA Breach Violations
          </div>
          <div style={{ fontSize: '28px', fontWeight: '800', color: violationCount > 0 ? '#dc2626' : '#0f172a' }}>
            {violationCount}
          </div>
          <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>Target deadlines exceeded</div>
        </div>

        <div style={kpiCardStyle}>
          <div style={{ fontSize: '12px', fontWeight: '700', color: '#64748b', textTransform: 'uppercase', marginBottom: '8px' }}>
            SLA Compliance Rate
          </div>
          <div style={{ fontSize: '28px', fontWeight: '800', color: Number(complianceRate) >= 90 ? '#16a34a' : '#d97706' }}>
            {complianceRate}%
          </div>
          <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>Based on active open incidents</div>
        </div>

        <div style={kpiCardStyle}>
          <div style={{ fontSize: '12px', fontWeight: '700', color: '#64748b', textTransform: 'uppercase', marginBottom: '8px' }}>
            Monitored Open Incidents
          </div>
          <div style={{ fontSize: '28px', fontWeight: '800', color: '#0f172a' }}>
            {totalOpen}
          </div>
          <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>Excludes closed tickets</div>
        </div>
      </div>

      {/* Main View Container */}
      <div style={{ backgroundColor: '#fff', borderRadius: '12px', border: '1px solid #e2e8f0', overflow: 'hidden' }}>
        {/* Navigation Tabs & Search Filter */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '16px 20px',
            borderBottom: '1px solid #e2e8f0',
            backgroundColor: '#f8fafc',
            flexWrap: 'wrap',
            gap: '12px',
          }}
        >
          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              onClick={() => setActiveTab('at-risk')}
              style={tabBtnStyle(activeTab === 'at-risk')}
            >
              ⚠️ At-Risk ({warningCount})
            </button>
            <button
              onClick={() => setActiveTab('violations')}
              style={tabBtnStyle(activeTab === 'violations')}
            >
              🚨 Violations ({violationCount})
            </button>
            <button
              onClick={() => setActiveTab('all')}
              style={tabBtnStyle(activeTab === 'all')}
            >
              📋 All Open Incidents ({totalOpen})
            </button>
          </div>

          <div>
            <input
              type="text"
              placeholder="Filter by Incident ID..."
              value={filterIncidentId}
              onChange={(e) => setFilterIncidentId(e.target.value)}
              style={{
                padding: '8px 12px',
                fontSize: '13px',
                borderRadius: '6px',
                border: '1px solid #cbd5e1',
                outline: 'none',
                width: '200px',
              }}
            />
          </div>
        </div>

        {/* Tab 1: At-Risk Warnings Table */}
        {activeTab === 'at-risk' && (
          <div>
            {filteredWarnings.length === 0 ? (
              <div style={{ padding: '48px', textAlign: 'center', color: '#64748b', fontSize: '14px' }}>
                🎉 No active SLA warnings! All incidents are well within targets.
              </div>
            ) : (
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
                <thead>
                  <tr style={{ backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#64748b' }}>
                    <th style={thStyle}>Incident ID</th>
                    <th style={thStyle}>Title</th>
                    <th style={thStyle}>SLA Dimension</th>
                    <th style={thStyle}>Alert Kind</th>
                    <th style={thStyle}>Target Deadline</th>
                    <th style={thStyle}>Warning Raised At</th>
                    <th style={thStyle}>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredWarnings.map((item) => (
                    <tr key={item.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                      <td style={tdStyle}>
                        <Link to={`/incidents/${item.incidentId}`} style={{ fontWeight: '700', color: '#2563eb', textDecoration: 'none' }}>
                          {item.incidentId}
                        </Link>
                      </td>
                      <td style={{ ...tdStyle, maxWidth: '240px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {incidentMap[item.incidentId]?.title || '—'}
                      </td>
                      <td style={tdStyle}>
                        <span style={badgeStyle('#fef3c7', '#b45309')}>
                          {item.slaType} SLA
                        </span>
                      </td>
                      <td style={tdStyle}>
                        <span style={badgeStyle('#fffbeb', '#d97706')}>
                          ⚠️ {item.kind}
                        </span>
                      </td>
                      <td style={tdStyle}>{new Date(item.deadline).toLocaleString()}</td>
                      <td style={tdStyle}>{new Date(item.raisedAt).toLocaleString()}</td>
                      <td style={tdStyle}>
                        <Link
                          to={`/incidents/${item.incidentId}`}
                          style={{
                            padding: '4px 10px',
                            borderRadius: '4px',
                            backgroundColor: '#2563eb',
                            color: '#fff',
                            textDecoration: 'none',
                            fontSize: '12px',
                            fontWeight: '600',
                          }}
                        >
                          View Incident →
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}

        {/* Tab 2: Breach Violations Table */}
        {activeTab === 'violations' && (
          <div>
            {filteredViolations.length === 0 ? (
              <div style={{ padding: '48px', textAlign: 'center', color: '#64748b', fontSize: '14px' }}>
                ✓ Zero recorded SLA breaches. Excellent compliance!
              </div>
            ) : (
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
                <thead>
                  <tr style={{ backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#64748b' }}>
                    <th style={thStyle}>Incident ID</th>
                    <th style={thStyle}>Title</th>
                    <th style={thStyle}>SLA Dimension</th>
                    <th style={thStyle}>Alert Kind</th>
                    <th style={thStyle}>Target Deadline</th>
                    <th style={thStyle}>Breached At</th>
                    <th style={thStyle}>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredViolations.map((item) => (
                    <tr key={item.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                      <td style={tdStyle}>
                        <Link to={`/incidents/${item.incidentId}`} style={{ fontWeight: '700', color: '#2563eb', textDecoration: 'none' }}>
                          {item.incidentId}
                        </Link>
                      </td>
                      <td style={{ ...tdStyle, maxWidth: '240px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {incidentMap[item.incidentId]?.title || '—'}
                      </td>
                      <td style={tdStyle}>
                        <span style={badgeStyle('#fee2e2', '#b91c1c')}>
                          {item.slaType} SLA
                        </span>
                      </td>
                      <td style={tdStyle}>
                        <span style={badgeStyle('#fef2f2', '#dc2626')}>
                          🚨 {item.kind}
                        </span>
                      </td>
                      <td style={tdStyle}>{new Date(item.deadline).toLocaleString()}</td>
                      <td style={tdStyle}>{new Date(item.raisedAt).toLocaleString()}</td>
                      <td style={tdStyle}>
                        <Link
                          to={`/incidents/${item.incidentId}`}
                          style={{
                            padding: '4px 10px',
                            borderRadius: '4px',
                            backgroundColor: '#dc2626',
                            color: '#fff',
                            textDecoration: 'none',
                            fontSize: '12px',
                            fontWeight: '600',
                          }}
                        >
                          Remediate →
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}

        {/* Tab 3: All Open Incidents SLA Status Table */}
        {activeTab === 'all' && (
          <div>
            {filteredOpen.length === 0 ? (
              <div style={{ padding: '48px', textAlign: 'center', color: '#64748b', fontSize: '14px' }}>
                No open incidents currently monitored.
              </div>
            ) : (
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
                <thead>
                  <tr style={{ backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#64748b' }}>
                    <th style={thStyle}>ID</th>
                    <th style={thStyle}>Title</th>
                    <th style={thStyle}>Priority</th>
                    <th style={thStyle}>Status</th>
                    <th style={thStyle}>Response Deadline</th>
                    <th style={thStyle}>Resolution Deadline</th>
                    <th style={thStyle}>Live SLA</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredOpen.map((inc) => (
                    <tr key={inc.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                      <td style={tdStyle}>
                        <Link to={`/incidents/${inc.id}`} style={{ fontWeight: '700', color: '#2563eb', textDecoration: 'none' }}>
                          {inc.id}
                        </Link>
                      </td>
                      <td style={tdStyle}>
                        <div style={{ fontWeight: '600', color: '#0f172a' }}>{inc.title}</div>
                      </td>
                      <td style={tdStyle}>
                        <PriorityBadge priority={inc.priority} />
                      </td>
                      <td style={tdStyle}>
                        <StatusBadge status={inc.status} />
                      </td>
                      <td style={tdStyle}>
                        {inc.responseDeadline ? new Date(inc.responseDeadline).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '—'}
                      </td>
                      <td style={tdStyle}>
                        {inc.resolutionDeadline ? new Date(inc.resolutionDeadline).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '—'}
                      </td>
                      <td style={tdStyle}>
                        <SlaBadge incident={inc} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

const kpiCardStyle = {
  backgroundColor: '#ffffff',
  borderRadius: '10px',
  padding: '20px',
  border: '1px solid #e2e8f0',
  boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.05)',
};

const tabBtnStyle = (active) => ({
  padding: '8px 16px',
  borderRadius: '6px',
  border: active ? '1px solid #2563eb' : '1px solid #cbd5e1',
  backgroundColor: active ? '#2563eb' : '#fff',
  color: active ? '#fff' : '#475569',
  fontSize: '13px',
  fontWeight: '600',
  cursor: 'pointer',
});

const thStyle = {
  padding: '12px 16px',
  fontWeight: '600',
  fontSize: '12px',
};

const tdStyle = {
  padding: '12px 16px',
  color: '#334155',
};

const badgeStyle = (bg, color) => ({
  display: 'inline-block',
  padding: '2px 8px',
  borderRadius: '4px',
  fontSize: '11px',
  fontWeight: '700',
  backgroundColor: bg,
  color,
});
