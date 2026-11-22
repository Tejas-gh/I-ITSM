import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { incidentApi, referenceApi } from '../api/apiClient';
import { useAuth } from '../context/AuthContext';
import StatusBadge from '../components/StatusBadge';
import PriorityBadge from '../components/PriorityBadge';
import SlaBadge from '../components/SlaBadge';
import { dateToInstant } from '../api/apiClient';
import { INCIDENT_STATUS, PRIORITY_LEVEL, SLA_STATUS } from '../constants/itsmConstants';

const EMPTY_FORM = {
  incidentId: '',
  dateFrom: '',
  dateTo: '',
  status: '',
  priority: '',
  categoryId: '',
  serviceId: '',
  technicianId: '',
  slaStatus: '',
};

/** Form values -> backend query parameters (dates become ISO instants). */
function toCriteria(form) {
  return {
    incidentId: form.incidentId,
    createdFrom: dateToInstant(form.dateFrom),
    createdTo: dateToInstant(form.dateTo, true),
    status: form.status,
    priority: form.priority,
    categoryId: form.categoryId,
    serviceId: form.serviceId,
    technicianId: form.technicianId,
    slaStatus: form.slaStatus,
  };
}

export default function IncidentListPage() {
  const { currentUser } = useAuth();
  const [incidents, setIncidents] = useState([]);
  const [services, setServices] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Search form (edited) and the criteria last submitted to the backend (applied)
  const [form, setForm] = useState(EMPTY_FORM);
  const [applied, setApplied] = useState(EMPTY_FORM);

  const setField = (name) => (e) => setForm((f) => ({ ...f, [name]: e.target.value }));

  // Reference data for the dropdowns
  useEffect(() => {
    async function fetchReference() {
      try {
        const [srvList, catList] = await Promise.all([referenceApi.listServices(), referenceApi.listCategories()]);
        setServices(srvList);
        setCategories(catList);
      } catch (err) {
        console.error('Failed to load reference data', err);
      }
    }
    fetchReference();
  }, []);

  // Server-side search: every submitted criterion is sent to GET /api/incidents; results are not filtered locally.
  useEffect(() => {
    let cancelled = false;
    async function search() {
      setLoading(true);
      setError('');
      try {
        const list = await incidentApi.listVisibleIncidents(currentUser, toCriteria(applied));
        if (!cancelled) {
          // Sort descending: newest incidents first (by createdAt or ID descending)
          const sorted = [...(list || [])].sort((a, b) => {
            if (b.createdAt && a.createdAt) {
              const diff = new Date(b.createdAt) - new Date(a.createdAt);
              if (diff !== 0) return diff;
            }
            return (b.id || '').localeCompare(a.id || '', undefined, { numeric: true });
          });
          setIncidents(sorted);
        }
      } catch (err) {
        if (!cancelled) {
          setIncidents([]);
          setError(err.message || 'Search failed');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    search();
    return () => {
      cancelled = true;
    };
  }, [currentUser, applied]);

  const handleSubmit = (e) => {
    e.preventDefault();
    setApplied({ ...form });
  };

  const handleReset = () => {
    setForm(EMPTY_FORM);
    setApplied({ ...EMPTY_FORM });
  };

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', padding: '32px 24px' }}>
      {/* Page Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: '800', color: '#0f172a', margin: '0 0 6px 0' }}>
            Incident Management Directory
          </h1>
          <p style={{ color: '#64748b', fontSize: '14px', margin: 0 }}>
            Filtered by role permissions for <strong>{currentUser.name}</strong> ({currentUser.role})
          </p>
        </div>
        {currentUser?.role === 'EMPLOYEE' && (
          <Link
            to="/incidents/new"
            style={{
              backgroundColor: '#2563eb',
              color: '#fff',
              padding: '10px 18px',
              borderRadius: '8px',
              fontWeight: '600',
              fontSize: '14px',
              textDecoration: 'none',
            }}
          >
            + Report Incident
          </Link>
        )}
      </div>

      {/* Search form: submitted to the backend (server-side search) */}
      <form
        onSubmit={handleSubmit}
        aria-label="Incident search"
        style={{
          backgroundColor: '#fff',
          borderRadius: '10px',
          padding: '16px 20px',
          border: '1px solid #e2e8f0',
          marginBottom: '20px',
        }}
      >
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
            gap: '12px',
          }}
        >
          <div>
            <label htmlFor="f-incidentId" style={filterLabelStyle}>Incident ID</label>
            <input id="f-incidentId" type="text" placeholder="e.g. INC1001" value={form.incidentId}
              onChange={setField('incidentId')} style={inputStyle} />
          </div>
          <div>
            <label htmlFor="f-dateFrom" style={filterLabelStyle}>Created From</label>
            <input id="f-dateFrom" type="date" value={form.dateFrom} onChange={setField('dateFrom')} style={inputStyle} />
          </div>
          <div>
            <label htmlFor="f-dateTo" style={filterLabelStyle}>Created To</label>
            <input id="f-dateTo" type="date" value={form.dateTo} onChange={setField('dateTo')} style={inputStyle} />
          </div>
          <div>
            <label htmlFor="f-status" style={filterLabelStyle}>Status</label>
            <select id="f-status" value={form.status} onChange={setField('status')} style={inputStyle}>
              <option value="">All Statuses</option>
              {Object.values(INCIDENT_STATUS).map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="f-priority" style={filterLabelStyle}>Priority</label>
            <select id="f-priority" value={form.priority} onChange={setField('priority')} style={inputStyle}>
              <option value="">All Priorities</option>
              {Object.values(PRIORITY_LEVEL).map((p) => (
                <option key={p} value={p}>{p}</option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="f-category" style={filterLabelStyle}>Category</label>
            <select id="f-category" value={form.categoryId} onChange={setField('categoryId')} style={inputStyle}>
              <option value="">All Categories</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="f-service" style={filterLabelStyle}>Service</label>
            <select id="f-service" value={form.serviceId} onChange={setField('serviceId')} style={inputStyle}>
              <option value="">All Services</option>
              {services.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="f-technician" style={filterLabelStyle}>Technician</label>
            <input id="f-technician" type="text" placeholder="Technician ID" value={form.technicianId}
              onChange={setField('technicianId')} style={inputStyle} />
          </div>
          <div>
            <label htmlFor="f-sla" style={filterLabelStyle}>SLA Status</label>
            <select id="f-sla" value={form.slaStatus} onChange={setField('slaStatus')} style={inputStyle}>
              <option value="">Any SLA Status</option>
              {Object.values(SLA_STATUS).map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>
        </div>
        <div style={{ display: 'flex', gap: '10px', marginTop: '14px' }}>
          <button type="submit" style={searchBtnStyle}>Search</button>
          <button type="button" onClick={handleReset} style={resetBtnStyle}>Reset</button>
        </div>
      </form>

      {/* Incident Table */}
      <div
        style={{
          backgroundColor: '#fff',
          borderRadius: '10px',
          border: '1px solid #e2e8f0',
          overflow: 'hidden',
          boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.05)',
        }}
      >
        {loading ? (
          <div style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>Loading incidents...</div>
        ) : error ? (
          <div role="alert" style={{ padding: '40px', textAlign: 'center', color: '#b91c1c' }}>{error}</div>
        ) : incidents.length === 0 ? (
          <div style={{ padding: '60px', textAlign: 'center', color: '#64748b' }}>
            <div style={{ fontSize: '28px', marginBottom: '8px' }}>🔍</div>
            <div style={{ fontWeight: '600', color: '#0f172a' }}>No incidents matched the selected criteria</div>
            <p style={{ fontSize: '13px', marginTop: '4px' }}>Try resetting your search criteria or report a new incident.</p>
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
              <thead>
                <tr style={{ backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#64748b' }}>
                  <th style={{ padding: '12px 16px' }}>Incident ID</th>
                  <th style={{ padding: '12px 16px' }}>Title & Description</th>
                  <th style={{ padding: '12px 16px' }}>Priority</th>
                  <th style={{ padding: '12px 16px' }}>Status</th>
                  <th style={{ padding: '12px 16px' }}>Category</th>
                  <th style={{ padding: '12px 16px' }}>Assignee</th>
                  <th style={{ padding: '12px 16px' }}>SLA Target</th>
                </tr>
              </thead>
              <tbody>
                {incidents.map((inc) => (
                  <tr
                    key={inc.id}
                    style={{
                      borderBottom: '1px solid #f1f5f9',
                      transition: 'background 0.1s',
                    }}
                  >
                    <td style={{ padding: '14px 16px', fontWeight: '700' }}>
                      <Link to={`/incidents/${inc.id}`} style={{ color: '#2563eb', textDecoration: 'none' }}>
                        {inc.id}
                      </Link>
                    </td>
                    <td style={{ padding: '14px 16px', maxWidth: '300px' }}>
                      <Link
                        to={`/incidents/${inc.id}`}
                        style={{ color: '#0f172a', fontWeight: '600', textDecoration: 'none', display: 'block' }}
                      >
                        {inc.title}
                      </Link>
                      <div
                        style={{
                          fontSize: '12px',
                          color: '#64748b',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          marginTop: '2px',
                        }}
                      >
                        {inc.description}
                      </div>
                    </td>
                    <td style={{ padding: '14px 16px' }}>
                      <PriorityBadge priority={inc.priority} score={inc.priorityScore} showScore />
                    </td>
                    <td style={{ padding: '14px 16px' }}>
                      <StatusBadge status={inc.status} />
                    </td>
                    <td style={{ padding: '14px 16px', color: '#475569' }}>
                      {inc.categoryId}
                    </td>
                    <td style={{ padding: '14px 16px', color: '#475569' }}>
                      {inc.assignedTechnicianId || <span style={{ color: '#94a3b8', fontStyle: 'italic' }}>Unassigned</span>}
                    </td>
                    <td style={{ padding: '14px 16px' }}>
                      <SlaBadge deadline={inc.resolutionDeadline} doneAt={inc.resolvedAt} label="Resolution" />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

const filterLabelStyle = {
  display: 'block',
  fontSize: '11px',
  fontWeight: '700',
  color: '#64748b',
  textTransform: 'uppercase',
  letterSpacing: '0.5px',
  marginBottom: '4px',
};

const inputStyle = {
  width: '100%',
  padding: '8px 10px',
  borderRadius: '6px',
  border: '1px solid #cbd5e1',
  fontSize: '13px',
  boxSizing: 'border-box',
  backgroundColor: '#ffffff',
  outline: 'none',
};

const searchBtnStyle = {
  padding: '8px 18px',
  borderRadius: '6px',
  border: 'none',
  backgroundColor: '#2563eb',
  color: '#fff',
  fontSize: '13px',
  fontWeight: '600',
  cursor: 'pointer',
};

const resetBtnStyle = {
  padding: '8px 18px',
  borderRadius: '6px',
  border: '1px solid #cbd5e1',
  backgroundColor: '#fff',
  color: '#334155',
  fontSize: '13px',
  fontWeight: '600',
  cursor: 'pointer',
};
