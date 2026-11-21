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
  const [allIncidents, setAllIncidents] = useState([]);
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
      setAllIncidents(incs || []);
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
          setAllIncidents(incs || []);
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

  // Every visible incident (including resolved/closed), so a recorded breach can be matched to its incident's status
  const incidentMap = {};
  allIncidents.forEach((i) => {
    incidentMap[i.id] = i;
  });
  // A breach is "active" while its incident is still open. Resolved/verified/closed incidents (and incidents that are not
  // visible to this user) keep their recorded breach as history: it still counts towards the compliance rate below.
  const isIncidentOpen = (incidentId) => {
    const inc = incidentMap[incidentId];
    return !!inc && ![INCIDENT_STATUS.RESOLVED, INCIDENT_STATUS.VERIFIED, INCIDENT_STATUS.CLOSED].includes(inc.status);
  };

  const totalOpen = openIncidents.length;
  const violationCount = violations.length; // all recorded breaches (history included): drives the compliance rate
  const activeViolationCount = violations.filter((v) => isIncidentOpen(v.incidentId)).length;
  const warningCount = warnings.length; // all recorded warnings (history included)
  const activeWarningCount = warnings.filter((w) => isIncidentOpen(w.incidentId)).length;
  const complianceRate = totalOpen > 0 ? Math.max(0, ((totalOpen - violationCount) / totalOpen) * 100).toFixed(1) : '100.0';

  const filteredWarnings = warnings.filter((w) =>
    filterIncidentId ? w.incidentId?.toLowerCase().includes(filterIncidentId.toLowerCase()) : true
  );
  const activeWarnings = filteredWarnings.filter((w) => isIncidentOpen(w.incidentId));
  const historicalWarnings = filteredWarnings.filter((w) => !isIncidentOpen(w.incidentId));

  const filteredViolations = violations.filter((v) =>
    filterIncidentId ? v.incidentId?.toLowerCase().includes(filterIncidentId.toLowerCase()) : true
  );
  const activeViolations = filteredViolations.filter((v) => isIncidentOpen(v.incidentId));
  const historicalViolations = filteredViolations.filter((v) => !isIncidentOpen(v.incidentId));

  const filteredOpen = openIncidents.filter((i) =>
    filterIncidentId ? i.id?.toLowerCase().includes(filterIncidentId.toLowerCase()) : true
  );



  // One table of recorded warnings. Active ones are highlighted; historical ones are shown muted.
  const renderWarningRows = (list, active) => (
    <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
      <thead>
        <tr style={{ backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#64748b' }}>
          <th style={thStyle}>Incident ID</th>
          <th style={thStyle}>Title</th>
          <th style={thStyle}>Incident Status</th>
          <th style={thStyle}>SLA Dimension</th>
          <th style={thStyle}>Alert Kind</th>
          <th style={thStyle}>Target Deadline</th>
          <th style={thStyle}>Warning Raised At</th>
          <th style={thStyle}>Action</th>
        </tr>
      </thead>
      <tbody>
        {list.map((item) => (
          <tr key={item.id} style={{ borderBottom: '1px solid #f1f5f9', opacity: active ? 1 : 0.8 }}>
            <td style={tdStyle}>
              <Link to={`/incidents/${item.incidentId}`} style={{ fontWeight: '700', color: '#2563eb', textDecoration: 'none' }}>
                {item.incidentId}
              </Link>
            </td>
            <td style={{ ...tdStyle, maxWidth: '240px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {incidentMap[item.incidentId]?.title || '—'}
            </td>
            <td style={tdStyle}>
              {incidentMap[item.incidentId] ? <StatusBadge status={incidentMap[item.incidentId].status} size="small" /> : '—'}
            </td>
            <td style={tdStyle}>
              <span style={badgeStyle('#fef3c7', '#b45309')}>{item.slaType} SLA</span>
            </td>
            <td style={tdStyle}>
              <span style={active ? badgeStyle('#fffbeb', '#d97706') : badgeStyle('#f1f5f9', '#475569')}>
                {active ? '⚠️' : '🗂️'} {item.kind}
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
                  backgroundColor: active ? '#2563eb' : '#e2e8f0',
                  color: active ? '#fff' : '#334155',
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
  );

  // One table of recorded breaches. Active ones are highlighted; historical ones are shown muted.
  const renderViolationRows = (list, active) => (
    <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
      <thead>
        <tr style={{ backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#64748b' }}>
          <th style={thStyle}>Incident ID</th>
          <th style={thStyle}>Title</th>
          <th style={thStyle}>Incident Status</th>
          <th style={thStyle}>SLA Dimension</th>
          <th style={thStyle}>Alert Kind</th>
          <th style={thStyle}>Target Deadline</th>
          <th style={thStyle}>Breached At</th>
          <th style={thStyle}>Action</th>
        </tr>
      </thead>
      <tbody>
        {list.map((item) => (
          <tr key={item.id} style={{ borderBottom: '1px solid #f1f5f9', opacity: active ? 1 : 0.8 }}>
            <td style={tdStyle}>
              <Link to={`/incidents/${item.incidentId}`} style={{ fontWeight: '700', color: '#2563eb', textDecoration: 'none' }}>
                {item.incidentId}
              </Link>
            </td>
            <td style={{ ...tdStyle, maxWidth: '240px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {incidentMap[item.incidentId]?.title || '—'}
            </td>
            <td style={tdStyle}>
              {incidentMap[item.incidentId] ? <StatusBadge status={incidentMap[item.incidentId].status} size="small" /> : '—'}
            </td>
            <td style={tdStyle}>
              <span style={badgeStyle('#fee2e2', '#b91c1c')}>{item.slaType} SLA</span>
            </td>
            <td style={tdStyle}>
              <span style={active ? badgeStyle('#fef2f2', '#dc2626') : badgeStyle('#f1f5f9', '#475569')}>
                {active ? '🚨' : '🗂️'} {item.kind}
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
                  backgroundColor: active ? '#dc2626' : '#e2e8f0',
                  color: active ? '#fff' : '#334155',
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
          <div style={{ fontSize: '28px', fontWeight: '800', color: activeWarningCount > 0 ? '#d97706' : '#0f172a' }}>
            {activeWarningCount}
          </div>
          <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>
            Open incidents near their deadline · {warningCount} recorded in total
          </div>
        </div>

        <div style={kpiCardStyle}>
          <div style={{ fontSize: '12px', fontWeight: '700', color: '#64748b', textTransform: 'uppercase', marginBottom: '8px' }}>
            SLA Breach Violations
          </div>
          <div style={{ fontSize: '28px', fontWeight: '800', color: activeViolationCount > 0 ? '#dc2626' : '#0f172a' }}>
            {activeViolationCount}
          </div>
          <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>
            Open incidents in breach · {violationCount} recorded in total
          </div>
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
              ⚠️ At-Risk ({activeWarningCount})
            </button>
            <button
              onClick={() => setActiveTab('violations')}
              style={tabBtnStyle(activeTab === 'violations')}
            >
              🚨 Violations ({activeViolationCount})
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

        {/* Tab 1: At-Risk Warnings (active vs. historical) */}
        {activeTab === 'at-risk' && (
          <div>
            {filteredWarnings.length === 0 ? (
              <div style={{ padding: '48px', textAlign: 'center', color: '#64748b', fontSize: '14px' }}>
                🎉 No active SLA warnings! All incidents are well within targets.
              </div>
            ) : (
              <>
                <div style={violationSectionHeadStyle}>
                  <strong>Active at-risk ({activeWarnings.length})</strong>
                  <span style={{ color: '#64748b' }}> · the incident is still open</span>
                </div>
                {activeWarnings.length === 0 ? (
                  <div style={{ padding: '24px', textAlign: 'center', color: '#16a34a', fontSize: '14px' }}>
                    ✓ No open incident is currently at risk.
                  </div>
                ) : (
                  renderWarningRows(activeWarnings, true)
                )}

                {historicalWarnings.length > 0 && (
                  <>
                    <div style={{ ...violationSectionHeadStyle, borderTop: '1px solid #e2e8f0' }}>
                      <strong>Historical warnings ({historicalWarnings.length})</strong>
                      <span style={{ color: '#64748b' }}>
                        {' '}· recorded for audit; the incident is resolved or closed (or not visible to you).
                      </span>
                    </div>
                    {renderWarningRows(historicalWarnings, false)}
                  </>
                )}
              </>
            )}
          </div>
        )}

        {/* Tab 2: Breach Violations (active vs. historical) */}
        {activeTab === 'violations' && (
          <div>
            {filteredViolations.length === 0 ? (
              <div style={{ padding: '48px', textAlign: 'center', color: '#64748b', fontSize: '14px' }}>
                ✓ Zero recorded SLA breaches. Excellent compliance!
              </div>
            ) : (
              <>
                <div style={violationSectionHeadStyle}>
                  <strong>Active violations ({activeViolations.length})</strong>
                  <span style={{ color: '#64748b' }}> · the incident is still open</span>
                </div>
                {activeViolations.length === 0 ? (
                  <div style={{ padding: '24px', textAlign: 'center', color: '#16a34a', fontSize: '14px' }}>
                    ✓ No open incident is currently in breach.
                  </div>
                ) : (
                  renderViolationRows(activeViolations, true)
                )}

                {historicalViolations.length > 0 && (
                  <>
                    <div style={{ ...violationSectionHeadStyle, borderTop: '1px solid #e2e8f0' }}>
                      <strong>Historical breaches ({historicalViolations.length})</strong>
                      <span style={{ color: '#64748b' }}>
                        {' '}· recorded for audit; the incident is resolved or closed (or not visible to you). They still count in the compliance rate.
                      </span>
                    </div>
                    {renderViolationRows(historicalViolations, false)}
                  </>
                )}
              </>
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

const violationSectionHeadStyle = {
  padding: '14px 16px',
  fontSize: '13px',
  color: '#0f172a',
  backgroundColor: '#f8fafc',
  borderBottom: '1px solid #e2e8f0',
};
