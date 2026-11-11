import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { incidentApi, referenceApi } from '../api/apiClient';
import { useAuth } from '../context/AuthContext';
import StatusBadge from '../components/StatusBadge';
import PriorityBadge from '../components/PriorityBadge';
import SlaBadge from '../components/SlaBadge';
import { INCIDENT_STATUS, PRIORITY_LEVEL } from '../constants/itsmConstants';

export default function IncidentListPage() {
  const { currentUser } = useAuth();
  const [incidents, setIncidents] = useState([]);
  const [services, setServices] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);

  // Filter states
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [priorityFilter, setPriorityFilter] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [serviceFilter, setServiceFilter] = useState('');

  useEffect(() => {
    async function fetchData() {
      try {
        setLoading(true);
        const [incList, srvList, catList] = await Promise.all([
          incidentApi.listVisibleIncidents(currentUser),
          referenceApi.listServices(),
          referenceApi.listCategories(),
        ]);
        setIncidents(incList);
        setServices(srvList);
        setCategories(catList);
      } catch (err) {
        console.error('Failed to load incident list', err);
      } finally {
        setLoading(false);
      }
    }
    fetchData();
  }, [currentUser]);

  // Apply filters
  const filteredIncidents = incidents.filter((inc) => {
    if (statusFilter && inc.status !== statusFilter) return false;
    if (priorityFilter && inc.priority !== priorityFilter) return false;
    if (categoryFilter && inc.categoryId !== categoryFilter) return false;
    if (serviceFilter && inc.serviceId !== serviceFilter) return false;
    if (search) {
      const q = search.toLowerCase();
      const matchTitle = inc.title?.toLowerCase().includes(q);
      const matchDesc = inc.description?.toLowerCase().includes(q);
      const matchId = inc.id?.toLowerCase().includes(q);
      if (!matchTitle && !matchDesc && !matchId) return false;
    }
    return true;
  });

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
      </div>

      {/* Filter Toolbar */}
      <div
        style={{
          backgroundColor: '#fff',
          borderRadius: '10px',
          padding: '16px 20px',
          border: '1px solid #e2e8f0',
          marginBottom: '20px',
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
          gap: '12px',
        }}
      >
        <div>
          <label style={filterLabelStyle}>Search Text / ID</label>
          <input
            type="text"
            placeholder="Search title, desc..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={inputStyle}
          />
        </div>

        <div>
          <label style={filterLabelStyle}>Status</label>
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} style={inputStyle}>
            <option value="">All Statuses</option>
            {Object.values(INCIDENT_STATUS).map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label style={filterLabelStyle}>Priority</label>
          <select value={priorityFilter} onChange={(e) => setPriorityFilter(e.target.value)} style={inputStyle}>
            <option value="">All Priorities</option>
            {Object.values(PRIORITY_LEVEL).map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label style={filterLabelStyle}>Category</label>
          <select value={categoryFilter} onChange={(e) => setCategoryFilter(e.target.value)} style={inputStyle}>
            <option value="">All Categories</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label style={filterLabelStyle}>Service</label>
          <select value={serviceFilter} onChange={(e) => setServiceFilter(e.target.value)} style={inputStyle}>
            <option value="">All Services</option>
            {services.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </div>
      </div>

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
        ) : filteredIncidents.length === 0 ? (
          <div style={{ padding: '60px', textAlign: 'center', color: '#64748b' }}>
            <div style={{ fontSize: '28px', marginBottom: '8px' }}>🔍</div>
            <div style={{ fontWeight: '600', color: '#0f172a' }}>No incidents matched the selected criteria</div>
            <p style={{ fontSize: '13px', marginTop: '4px' }}>Try resetting your filter parameters or report a new incident.</p>
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
                {filteredIncidents.map((inc) => (
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
