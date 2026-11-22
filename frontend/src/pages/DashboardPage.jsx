import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { incidentApi, referenceApi, slaApi, dashboardApi } from '../api/apiClient';
import { useAuth } from '../context/AuthContext';
import StatusBadge from '../components/StatusBadge';
import PriorityBadge from '../components/PriorityBadge';
import SlaBadge from '../components/SlaBadge';
import TechnicianWorkloadPanel from '../components/TechnicianWorkloadPanel';
import { employeeMetrics, isAwaitingClosure, isResolved, scopeAlertsToIncidents } from './dashboardMetrics';
import { INCIDENT_STATUS, PRIORITY_LEVEL, PRIORITY_LABELS } from '../constants/itsmConstants';

/** "Display name (login id)"; just the id when no distinct display name is known (e.g. a token-only session). */
function greetingName(user) {
  return user.name && user.name !== user.id ? `${user.name} (${user.id})` : user.id;
}

export default function DashboardPage() {
  const { currentUser, isEmployee, isAdmin } = useAuth();
  const [incidents, setIncidents] = useState([]);
  const [services, setServices] = useState([]);
  const [atRiskAlerts, setAtRiskAlerts] = useState([]);
  const [violationAlerts, setViolationAlerts] = useState([]);
  const [workloads, setWorkloads] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadData() {
      try {
        setLoading(true);
        // Employees have no access to the staff-only SLA/dashboard endpoints; only administrators see workloads
        const [incList, srvList, atRiskList, violList, workloadList] = await Promise.all([
          incidentApi.listVisibleIncidents(currentUser),
          referenceApi.listServices(),
          isEmployee ? [] : slaApi.getAtRisk().catch(() => []),
          isEmployee ? [] : slaApi.getViolations().catch(() => []),
          isAdmin ? dashboardApi.getTechnicianWorkloads().catch(() => []) : [],
        ]);
        const visible = [...(incList || [])].sort((a, b) => {
          if (b.createdAt && a.createdAt) {
            const diff = new Date(b.createdAt) - new Date(a.createdAt);
            if (diff !== 0) return diff;
          }
          return (b.id || '').localeCompare(a.id || '', undefined, { numeric: true });
        });
        setIncidents(visible);
        setServices(srvList || []);
        // The SLA endpoints are not role-scoped: non-admins only count alerts of their own incidents
        setAtRiskAlerts(isAdmin ? atRiskList || [] : scopeAlertsToIncidents(atRiskList, visible));
        setViolationAlerts(isAdmin ? violList || [] : scopeAlertsToIncidents(violList, visible));
        setWorkloads(workloadList || []);
      } catch (err) {
        console.error('Failed to load dashboard data', err);
      } finally {
        setLoading(false);
      }
    }
    loadData();
  }, [currentUser, isEmployee, isAdmin]);

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
  const resolvedCount = incidents.filter(isResolved).length;
  // Administrators / IT Managers close VERIFIED incidents: show them separately instead of inside "Resolved"
  const awaitingClosureCount = incidents.filter(isAwaitingClosure).length;
  const adminResolvedCount = resolvedCount - awaitingClosureCount;
  const awaitingAckCount = incidents.filter((i) => i.status === INCIDENT_STATUS.ASSIGNED).length;
  const mine = employeeMetrics(incidents);

  const now = new Date();
  const calculatedBreaches = incidents.filter((i) => {
    if (i.status === INCIDENT_STATUS.CLOSED) return false;
    const resDeadline = i.resolutionDeadline ? new Date(i.resolutionDeadline) : null;
    return resDeadline && now > resDeadline && !i.resolvedAt;
  }).length;
  const totalViolations = Math.max(violationAlerts.length, calculatedBreaches);
  const complianceRate = total > 0 ? Math.max(0, Math.round(((total - totalViolations) / total) * 100)) : 100;

  // Workload / Service Breakdown
  const serviceCounts = {};
  incidents.forEach((inc) => {
    serviceCounts[inc.serviceId] = (serviceCounts[inc.serviceId] || 0) + 1;
  });

  // ---- Presentation helpers (derived from the figures above; no new data) ----
  const openIncidents = incidents.filter((i) => i.status !== INCIDENT_STATUS.RESOLVED
    && i.status !== INCIDENT_STATUS.VERIFIED && i.status !== INCIDENT_STATUS.CLOSED);
  const priorityOrder = [PRIORITY_LEVEL.P1, PRIORITY_LEVEL.P2, PRIORITY_LEVEL.P3, PRIORITY_LEVEL.P4];
  const openByPriority = priorityOrder.map((p) => ({
    label: PRIORITY_LABELS[p] || p,
    short: p,
    value: openIncidents.filter((i) => i.priority === p).length,
    color: PRIORITY_COLORS[p],
  }));
  // Active violations: breached incidents that are still open. Breaches on resolved/closed incidents stay recorded in
  // totalViolations (so the compliance rate still reflects them) but are no longer something to fix.
  const violatedIds = new Set(violationAlerts.map((a) => a.incidentId));
  const isOverdue = (i) => i.resolutionDeadline && now > new Date(i.resolutionDeadline) && !i.resolvedAt;
  const activeViolations = openIncidents.filter((i) => violatedIds.has(i.id) || isOverdue(i)).length;
  const recordedViolations = Math.max(totalViolations, activeViolations);
  // At-risk: only open incidents that carry a warning; warnings on resolved/closed incidents stay recorded as history
  const warnedIds = new Set(atRiskAlerts.map((a) => a.incidentId));
  const totalAtRisk = openIncidents.filter((i) => warnedIds.has(i.id)).length;
  const complianceColor = complianceRate >= 90 ? '#16a34a' : complianceRate >= 70 ? '#d97706' : '#dc2626';
  const waitingForMe = incidents.filter((i) => i.status === INCIDENT_STATUS.WAITING_FOR_USER);
  const toVerify = incidents.filter((i) => i.status === INCIDENT_STATUS.RESOLVED);
  // Technician: highest priority first, then earliest resolution deadline
  const myQueue = [...openIncidents].sort((a, b) => {
    const byPriority = priorityOrder.indexOf(a.priority) - priorityOrder.indexOf(b.priority);
    if (byPriority !== 0) return byPriority;
    return new Date(a.resolutionDeadline || '9999-12-31') - new Date(b.resolutionDeadline || '9999-12-31');
  });

  const header = (title, subtitle) => (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px', marginBottom: '24px' }}>
      <div>
        <h1 style={{ fontSize: '26px', fontWeight: '800', color: '#0f172a', margin: '0 0 6px 0' }}>{title}</h1>
        <p style={{ color: '#64748b', fontSize: '14px', margin: 0 }}>
          {subtitle} <strong style={{ color: '#0f172a' }}>{greetingName(currentUser)}</strong>
          <span style={roleChipStyle}>{currentUser.role}</span>
        </p>
      </div>
      {isEmployee && (
        <Link to="/incidents/new" style={primaryButtonStyle}>
          <span>+</span> Report New Incident
        </Link>
      )}
    </div>
  );

  // ---------------------------------------------------------------- EMPLOYEE
  if (isEmployee) {
    const banner = waitingForMe.length > 0
      ? { tone: 'amber', icon: '💬', title: `${waitingForMe.length} incident${waitingForMe.length > 1 ? 's need' : ' needs'} your response`,
          text: 'A technician is waiting for more information from you.', to: `/incidents/${waitingForMe[0].id}`, cta: 'Respond now' }
      : toVerify.length > 0
        ? { tone: 'blue', icon: '✅', title: `${toVerify.length} resolved incident${toVerify.length > 1 ? 's' : ''} waiting for your confirmation`,
            text: 'Please check the fix and confirm it, or reopen the incident.', to: `/incidents/${toVerify[0].id}`, cta: 'Review' }
        : { tone: 'green', icon: '👍', title: 'Nothing needs your attention right now',
            text: 'You will be notified when a technician needs something from you.' };

    return (
      <div style={pageStyle}>
        {header('My Support Overview', 'Hello,')}
        <Banner {...banner} />

        <SectionTitle>My Incidents at a Glance</SectionTitle>
        <div style={gridStyle}>
          <StatTile icon="📋" label="My Incidents" value={mine.total} color="#0f172a" sub="Reported by me" />
          <StatTile icon="🛠️" label="My Open Incidents" value={mine.open} color="#2563eb" sub="Still being worked on" />
          <StatTile icon="✅" label="My Resolved Incidents" value={mine.resolved} color="#16a34a" sub="Resolved, verified or closed" />
          <StatTile icon="💬" label="Awaiting My Response" value={mine.awaitingResponse}
            color={mine.awaitingResponse > 0 ? '#d97706' : '#16a34a'} sub="Technician needs information" />
          <StatTile icon="⏱️" label="My SLA Missed" value={mine.slaMissed}
            color={mine.slaMissed > 0 ? '#dc2626' : '#16a34a'} sub="Resolution target missed" />
        </div>

        <SectionTitle action={<Link to="/incidents" style={linkStyle}>View all my incidents →</Link>}>
          My Recent Incidents
        </SectionTitle>
        <div style={{ ...cardStyle, padding: 0 }}>
          <IncidentTable
            incidents={incidents.slice(0, 5)}
            hint={(inc) => (inc.status === INCIDENT_STATUS.WAITING_FOR_USER ? { text: 'Response needed', color: '#b45309', bg: '#fef3c7' }
              : inc.status === INCIDENT_STATUS.RESOLVED ? { text: 'Please confirm', color: '#1d4ed8', bg: '#dbeafe' } : null)}
            empty={{ title: 'You have not reported any incidents yet', text: 'When something is not working, report it and we will take it from there.', to: '/incidents/new', cta: 'Report an incident' }}
          />
        </div>
      </div>
    );
  }

  // -------------------------------------------------------------- TECHNICIAN
  if (!isAdmin) {
    const banner = activeViolations > 0
      ? { tone: 'red', icon: '🚨', title: `${activeViolations} of your open incidents ${activeViolations > 1 ? 'have' : 'has'} breached its SLA`,
          text: 'Prioritise these first.', to: '/sla', cta: 'Open SLA Monitor' }
      : awaitingAckCount > 0
        ? { tone: 'amber', icon: '🔔', title: `${awaitingAckCount} assigned incident${awaitingAckCount > 1 ? 's are' : ' is'} waiting for acknowledgement`,
            text: 'Acknowledge them to start the work and meet the response SLA.', to: '/incidents', cta: 'View my incidents' }
        : { tone: 'green', icon: '👍', title: 'You are all caught up',
            text: 'No unacknowledged incidents and no SLA breaches on your queue.' };

    return (
      <div style={pageStyle}>
        {header('My Workload', 'Assigned to')}
        <Banner {...banner} />

        <SectionTitle>Needs Your Attention</SectionTitle>
        <div style={gridStyle}>
          <StatTile icon="🔔" label="Awaiting Acknowledgement" value={awaitingAckCount}
            color={awaitingAckCount > 0 ? '#d97706' : '#16a34a'} sub="Acknowledge to meet response SLA" to="/incidents" />
          <StatTile icon="🔥" label="P1 Critical" value={criticalCount} color="#dc2626" sub="Immediate escalation" />
          <StatTile label="⚠️ SLA At-Risk" value={totalAtRisk} color={totalAtRisk > 0 ? '#d97706' : '#16a34a'}
            sub="Within 20% deadline threshold" to="/sla" />
          <StatTile label="🚨 SLA Violations" value={activeViolations} color={activeViolations > 0 ? '#dc2626' : '#16a34a'}
            sub={`Open incidents in breach · ${recordedViolations} recorded`} to="/sla" />
        </div>

        <SectionTitle>My Workload</SectionTitle>
        <div style={{ ...gridStyle, marginBottom: '16px' }}>
          <StatTile icon="📥" label="Assigned to Me" value={total} color="#0f172a" sub="All incidents assigned to me" />
          <StatTile icon="🛠️" label="Open & Active" value={openCount} color="#2563eb" sub="Require resolution" />
          <StatTile label="📈 SLA Compliance" value={`${complianceRate}%`} color={complianceColor} sub="Target ≥ 95%"
            to="/sla" progress={complianceRate} />
        </div>
        <div style={{ ...cardStyle, marginBottom: '32px' }}>
          <h2 style={cardTitleStyle}>Active Work by Priority</h2>
          <BreakdownBar items={openByPriority} emptyText="No active incidents in your queue." />
        </div>

        <SectionTitle action={<Link to="/incidents" style={linkStyle}>View all assigned incidents →</Link>}>
          My Queue (highest priority first)
        </SectionTitle>
        <div style={{ ...cardStyle, padding: 0 }}>
          <IncidentTable
            incidents={myQueue.slice(0, 8)}
            empty={{ title: 'No incidents are assigned to you', text: 'New assignments will appear here and in your notifications.' }}
          />
        </div>
      </div>
    );
  }

  // ------------------------------------------------------------------- ADMIN
  const banner = activeViolations > 0
    ? { tone: 'red', icon: '🚨', title: `${activeViolations} open incident${activeViolations > 1 ? 's are' : ' is'} in SLA breach`,
        text: `${totalAtRisk} more at risk · ${criticalCount} P1 critical incident${criticalCount === 1 ? '' : 's'}.`, to: '/sla', cta: 'Open SLA Monitor' }
    : totalAtRisk > 0
      ? { tone: 'amber', icon: '⚠️', title: `${totalAtRisk} incident${totalAtRisk > 1 ? 's are' : ' is'} close to breaching the SLA`,
          text: 'No violations so far.', to: '/sla', cta: 'Open SLA Monitor' }
      : { tone: 'green', icon: '👍', title: 'All SLAs are healthy', text: 'No violations and nothing at risk right now.' };

  return (
    <div style={pageStyle}>
      {header('ITSM Service Desk Overview', 'Viewing perspective for')}
      <Banner {...banner} />

      <SectionTitle>Incident Overview</SectionTitle>
      <div style={gridStyle}>
        <StatTile icon="📋" label="Total Incidents" value={total} color="#0f172a" sub="Visible in role scope" />
        <StatTile icon="🛠️" label="Open & Active" value={openCount} color="#2563eb" sub="Require resolution" />
        <StatTile icon="✅" label="Resolved" value={isAdmin ? adminResolvedCount : resolvedCount} color="#16a34a"
          sub={isAdmin ? 'Resolved or closed' : 'Resolved, verified or closed'} />
        {isAdmin && (
          <StatTile icon="🔒" label="Awaiting Closure" value={awaitingClosureCount}
            color={awaitingClosureCount > 0 ? '#d97706' : '#16a34a'} sub="Verified · ready for final closure" to="/incidents" />
        )}
        <StatTile icon="🔥" label="P1 Critical" value={criticalCount} color="#dc2626" sub="Immediate escalation" />
      </div>

      <SectionTitle>SLA Health</SectionTitle>
      <div style={gridStyle}>
        <StatTile label="⚠️ SLA At-Risk" value={totalAtRisk} color={totalAtRisk > 0 ? '#d97706' : '#16a34a'}
          sub="Within 20% deadline threshold" to="/sla" />
        <StatTile label="🚨 SLA Violations" value={activeViolations} color={activeViolations > 0 ? '#dc2626' : '#16a34a'}
          sub={`Open incidents in breach · ${recordedViolations} recorded`} to="/sla" />
        <StatTile label="📈 SLA Compliance" value={`${complianceRate}%`} color={complianceColor} sub="Target ≥ 95%"
          to="/sla" progress={complianceRate} />
      </div>

      <div style={twoColumnStyle}>
        <div style={cardStyle}>
          <h2 style={cardTitleStyle}>Incident Health</h2>
          <div style={subHeadingStyle}>Open vs. resolved</div>
          <BreakdownBar
            items={[
              { label: 'Open', value: openCount, color: '#2563eb' },
              ...(isAdmin ? [{ label: 'Awaiting closure', value: awaitingClosureCount, color: '#d97706' }] : []),
              { label: 'Closed out', value: isAdmin ? adminResolvedCount : resolvedCount, color: '#16a34a' },
            ]}
            emptyText="No incidents yet."
          />
          <div style={{ ...subHeadingStyle, marginTop: '20px' }}>Open incidents by priority</div>
          <BreakdownBar items={openByPriority} emptyText="No open incidents." />
        </div>
        <TechnicianWorkloadPanel workloads={workloads} />
      </div>

      <div style={{ ...twoColumnStyle, gridTemplateColumns: 'minmax(260px, 1fr) minmax(0, 2fr)' }}>
        {/* Service Analysis */}
        <div style={{ ...cardStyle, height: 'fit-content' }}>
          <h2 style={cardTitleStyle}>Service Incident Distribution</h2>
          {services.length === 0 ? (
            <div style={{ color: '#64748b', fontSize: '13px' }}>No services defined.</div>
          ) : (
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
                      <div style={{ width: `${percent}%`, height: '100%', background: '#3b82f6', borderRadius: '3px' }} />
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Recent Incidents */}
        <div>
          <SectionTitle action={<Link to="/incidents" style={linkStyle}>View all incidents →</Link>} flush>
            Recent Incidents
          </SectionTitle>
          <div style={{ ...cardStyle, padding: 0 }}>
            <IncidentTable incidents={incidents.slice(0, 5)} empty={{ title: 'No incidents yet', text: 'Incidents reported by employees will appear here.' }} />
          </div>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------------------------------------------------
// Small presentational components (this file only)

/** KPI tile. With `to` it is a link (used for the SLA tiles); `progress` adds a thin bar (0-100). */
function StatTile({ icon, label, value, sub, color, to, progress }) {
  const style = { ...cardStyle, borderTop: `3px solid ${color}`, position: 'relative', display: 'block' };
  const body = (
    <>
      {icon && <span aria-hidden="true" style={tileIconStyle}>{icon}</span>}
      <div style={metricLabelStyle}>{label}</div>
      <div style={{ ...metricValueStyle, color }}>{value}</div>
      <div style={metricSubStyle}>{sub}{to ? ' →' : ''}</div>
      {progress !== undefined && (
        <div style={{ height: '6px', background: '#e2e8f0', borderRadius: '3px', overflow: 'hidden', marginTop: '10px' }}>
          <div style={{ width: `${Math.max(0, Math.min(100, progress))}%`, height: '100%', background: color }} />
        </div>
      )}
    </>
  );
  if (!to) return <div style={style}>{body}</div>;
  return (
    <Link to={to} style={{ ...style, textDecoration: 'none', cursor: 'pointer' }}>
      {body}
    </Link>
  );
}

function SectionTitle({ children, action, flush }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', margin: flush ? '0 0 12px 0' : '0 0 12px 0' }}>
      <h2 style={{ fontSize: '15px', fontWeight: '700', color: '#334155', margin: 0, textTransform: 'uppercase', letterSpacing: '0.6px' }}>
        {children}
      </h2>
      {action}
    </div>
  );
}

const BANNER_TONES = {
  red: { bg: '#fef2f2', border: '#fecaca', title: '#991b1b', text: '#b91c1c' },
  amber: { bg: '#fffbeb', border: '#fde68a', title: '#92400e', text: '#b45309' },
  blue: { bg: '#eff6ff', border: '#bfdbfe', title: '#1e40af', text: '#1d4ed8' },
  green: { bg: '#f0fdf4', border: '#bbf7d0', title: '#166534', text: '#15803d' },
};

/** The one thing the user should know or do right now. */
function Banner({ tone, icon, title, text, to, cta }) {
  const c = BANNER_TONES[tone];
  return (
    <div role="status" style={{ display: 'flex', alignItems: 'center', gap: '14px', flexWrap: 'wrap', backgroundColor: c.bg,
      border: `1px solid ${c.border}`, borderRadius: '12px', padding: '16px 20px', marginBottom: '28px' }}>
      <span aria-hidden="true" style={{ fontSize: '24px' }}>{icon}</span>
      <div style={{ flex: 1, minWidth: '220px' }}>
        <div style={{ fontWeight: '700', fontSize: '15px', color: c.title }}>{title}</div>
        <div style={{ fontSize: '13px', color: c.text, marginTop: '2px' }}>{text}</div>
      </div>
      {to && (
        <Link to={to} style={{ ...primaryButtonStyle, backgroundColor: c.title, fontSize: '13px', padding: '8px 16px' }}>{cta}</Link>
      )}
    </div>
  );
}

/** Stacked proportion bar with a legend. */
function BreakdownBar({ items, emptyText }) {
  const total = items.reduce((sum, i) => sum + i.value, 0);
  if (total === 0) return <div style={{ color: '#64748b', fontSize: '13px' }}>{emptyText}</div>;
  return (
    <div>
      <div style={{ display: 'flex', height: '10px', borderRadius: '5px', overflow: 'hidden', background: '#e2e8f0' }}>
        {items.filter((i) => i.value > 0).map((i) => (
          <div key={i.label} title={`${i.label}: ${i.value}`} style={{ width: `${(i.value / total) * 100}%`, background: i.color }} />
        ))}
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px 16px', marginTop: '10px' }}>
        {items.map((i) => (
          <span key={i.label} style={{ fontSize: '12px', color: '#475569', display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: i.color, display: 'inline-block' }} />
            {i.label} <strong style={{ color: '#0f172a' }}>{i.value}</strong>
          </span>
        ))}
      </div>
    </div>
  );
}

/** Incident table shared by all roles; `hint` adds an optional action chip to a row, `empty` is the empty state. */
function IncidentTable({ incidents, hint, empty }) {
  if (incidents.length === 0) {
    return (
      <div style={{ padding: '36px 20px', textAlign: 'center' }}>
        <div style={{ fontSize: '28px' }} aria-hidden="true">📭</div>
        <div style={{ fontWeight: '700', color: '#334155', marginTop: '8px' }}>{empty.title}</div>
        <div style={{ color: '#64748b', fontSize: '13px', margin: '4px 0 14px' }}>{empty.text}</div>
        {empty.to && <Link to={empty.to} style={{ ...primaryButtonStyle, fontSize: '13px', padding: '8px 16px' }}>{empty.cta}</Link>}
      </div>
    );
  }
  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
        <thead>
          <tr style={{ borderBottom: '1px solid #e2e8f0', color: '#64748b', backgroundColor: '#f8fafc' }}>
            <th style={thStyle}>ID</th>
            <th style={thStyle}>Title</th>
            <th style={thStyle}>Priority</th>
            <th style={thStyle}>Status</th>
            <th style={thStyle}>Resolution SLA</th>
          </tr>
        </thead>
        <tbody>
          {incidents.map((inc) => {
            const chip = hint ? hint(inc) : null;
            return (
              <tr key={inc.id} style={{ borderBottom: '1px solid #f1f5f9', backgroundColor: chip ? chip.bg + '55' : undefined }}>
                <td style={{ padding: '12px', fontWeight: '700' }}>
                  <Link to={`/incidents/${inc.id}`} style={{ color: '#2563eb', textDecoration: 'none' }}>{inc.id}</Link>
                </td>
                <td style={{ padding: '12px' }}>
                  <Link to={`/incidents/${inc.id}`} style={{ color: '#0f172a', textDecoration: 'none', fontWeight: '600' }}>{inc.title}</Link>
                  {chip && (
                    <span style={{ marginLeft: '8px', padding: '2px 8px', borderRadius: '10px', fontSize: '11px', fontWeight: '700',
                      color: chip.color, backgroundColor: chip.bg }}>{chip.text}</span>
                  )}
                </td>
                <td style={{ padding: '12px' }}><PriorityBadge priority={inc.priority} /></td>
                <td style={{ padding: '12px' }}><StatusBadge status={inc.status} /></td>
                <td style={{ padding: '12px' }}>
                  <SlaBadge deadline={inc.resolutionDeadline} doneAt={inc.resolvedAt} label="Resolution" />
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

const PRIORITY_COLORS = { P1: '#dc2626', P2: '#ea580c', P3: '#0284c7', P4: '#9ca3af' };

const pageStyle = { maxWidth: '1200px', margin: '0 auto', padding: '32px 24px' };
const gridStyle = { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px', marginBottom: '32px' };
const twoColumnStyle = { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '24px', marginBottom: '24px' };
const primaryButtonStyle = {
  backgroundColor: '#2563eb', color: '#fff', padding: '10px 20px', borderRadius: '8px', fontWeight: '600', fontSize: '14px',
  textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: '8px', boxShadow: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
};
const roleChipStyle = {
  marginLeft: '10px', padding: '2px 10px', borderRadius: '10px', fontSize: '11px', fontWeight: '700',
  color: '#475569', backgroundColor: '#e2e8f0', letterSpacing: '0.4px',
};
const linkStyle = { fontSize: '13px', fontWeight: '600', color: '#2563eb', textDecoration: 'none' };
const cardTitleStyle = { fontSize: '16px', fontWeight: '700', color: '#0f172a', margin: '0 0 16px 0' };
const subHeadingStyle = { fontSize: '12px', fontWeight: '600', color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '8px' };
const thStyle = { padding: '10px 12px', fontWeight: '600' };
const tileIconStyle = { position: 'absolute', top: '14px', right: '16px', fontSize: '20px', opacity: 0.85 };

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
