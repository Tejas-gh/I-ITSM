import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { incidentApi, referenceApi, calculatePriorityScore } from '../api/apiClient';
import { useAuth } from '../context/AuthContext';
import PriorityBadge from '../components/PriorityBadge';
import {
  IMPACT,
  URGENCY,
  CATEGORY_SOURCE,
} from '../constants/itsmConstants';

export default function CreateIncidentPage() {
  const { currentUser } = useAuth();
  const navigate = useNavigate();

  const [services, setServices] = useState([]);
  const [categories, setCategories] = useState([]);

  // Form fields matching CreateIncidentCommand (§4.1)
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [serviceId, setServiceId] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [suggestedCategoryId, setSuggestedCategoryId] = useState('');
  const [categorySource, setCategorySource] = useState(CATEGORY_SOURCE.MANUAL);
  const [impact, setImpact] = useState(IMPACT.MEDIUM);
  const [urgency, setUrgency] = useState(URGENCY.MEDIUM);
  const [affectedUsers, setAffectedUsers] = useState(1);

  // Suggestion UI state
  const [suggesting, setSuggesting] = useState(false);
  const [suggestionMessage, setSuggestionMessage] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    async function loadMetadata() {
      try {
        const [srvList, catList] = await Promise.all([
          referenceApi.listServices(),
          referenceApi.listActiveCategories(),
        ]);
        setServices(srvList);
        setCategories(catList);
        if (srvList.length > 0) setServiceId(srvList[0].id);
        if (catList.length > 0) setCategoryId(catList[0].id);
      } catch (err) {
        console.error('Failed loading metadata', err);
      }
    }
    loadMetadata();
  }, []);

  // Compute live priority preview based on §K
  const selectedService = services.find((s) => s.id === serviceId);
  const criticality = selectedService ? selectedService.businessCriticality : 'MEDIUM';
  const { score: liveScore, level: livePriority } = calculatePriorityScore(
    impact,
    urgency,
    criticality,
    affectedUsers
  );

  // Category classifier hook (§4.7)
  const handleSuggestCategory = async () => {
    if (!title && !description) {
      alert('Please enter a title or description first');
      return;
    }
    try {
      setSuggesting(true);
      setSuggestionMessage(null);
      const suggestion = await incidentApi.suggestCategory(title, description);
      if (suggestion && suggestion.categoryId) {
        setCategoryId(suggestion.categoryId);
        setSuggestedCategoryId(suggestion.categoryId);
        setCategorySource(suggestion.source || CATEGORY_SOURCE.KEYWORD);
        setSuggestionMessage({
          type: 'success',
          text: `Suggested "${suggestion.categoryId}" (${Math.round(suggestion.confidence * 100)}% confidence): ${suggestion.reason}`,
        });
      } else {
        setSuggestionMessage({
          type: 'info',
          text: 'No automated category suggestion was identified from the given text.',
        });
      }
    } catch (err) {
      console.error(err);
      setSuggestionMessage({ type: 'error', text: 'Error fetching suggestion' });
    } finally {
      setSuggesting(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);

    try {
      const payload = {
        title,
        description,
        serviceId,
        categoryId,
        suggestedCategoryId: suggestedCategoryId || null,
        categorySource,
        impact,
        urgency,
        affectedUsers: Number(affectedUsers),
      };

      const created = await incidentApi.createIncident(payload, currentUser);
      navigate(`/incidents/${created.id}`);
    } catch (err) {
      setError(err.message || 'Failed to create incident');
      setSubmitting(false);
    }
  };

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto', padding: '32px 24px' }}>
      <div style={{ marginBottom: '24px' }}>
        <h1 style={{ fontSize: '24px', fontWeight: '800', color: '#0f172a', margin: '0 0 6px 0' }}>
          Report New IT Incident
        </h1>
        <p style={{ color: '#64748b', fontSize: '14px', margin: 0 }}>
          Reporting as <strong>{currentUser.name}</strong> ({currentUser.department || currentUser.role})
        </p>
      </div>

      {error && (
        <div
          style={{
            padding: '12px 16px',
            backgroundColor: '#fee2e2',
            border: '1px solid #fca5a5',
            borderRadius: '8px',
            color: '#991b1b',
            fontSize: '14px',
            marginBottom: '20px',
          }}
        >
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} style={formCardStyle}>
        {/* Title */}
        <div style={{ marginBottom: '20px' }}>
          <label style={labelStyle}>Incident Title *</label>
          <input
            type="text"
            required
            maxLength={200}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g., Payment gateway 504 timeout during checkout"
            style={inputStyle}
          />
          <div style={helpTextStyle}>Brief, descriptive summary of the disruption (1..200 chars).</div>
        </div>

        {/* Description */}
        <div style={{ marginBottom: '20px' }}>
          <label style={labelStyle}>Detailed Description *</label>
          <textarea
            required
            rows={5}
            maxLength={4000}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Describe what occurred, steps to reproduce, error codes, and symptoms..."
            style={inputStyle}
          />
        </div>

        {/* Affected Service */}
        <div style={{ marginBottom: '20px' }}>
          <label style={labelStyle}>Affected IT Service *</label>
          <select value={serviceId} onChange={(e) => setServiceId(e.target.value)} style={inputStyle}>
            {services.map((srv) => (
              <option key={srv.id} value={srv.id}>
                {srv.name} (Criticality: {srv.businessCriticality})
              </option>
            ))}
          </select>
        </div>

        {/* Incident Category & Classifier Hook */}
        <div style={{ marginBottom: '20px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
            <label style={{ ...labelStyle, marginBottom: 0 }}>Category *</label>
            <button
              type="button"
              onClick={handleSuggestCategory}
              disabled={suggesting}
              style={{
                background: '#eff6ff',
                color: '#2563eb',
                border: '1px solid #bfdbfe',
                borderRadius: '6px',
                padding: '4px 10px',
                fontSize: '12px',
                fontWeight: '600',
                cursor: 'pointer',
              }}
            >
              {suggesting ? 'Analyzing text...' : '⚡ Suggest Category (AI/Hook)'}
            </button>
          </div>

          <select
            value={categoryId}
            onChange={(e) => {
              setCategoryId(e.target.value);
              setCategorySource(CATEGORY_SOURCE.MANUAL);
            }}
            style={inputStyle}
          >
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>

          {suggestionMessage && (
            <div
              style={{
                marginTop: '8px',
                padding: '8px 12px',
                borderRadius: '6px',
                fontSize: '12px',
                backgroundColor: suggestionMessage.type === 'success' ? '#f0fdf4' : '#f8fafc',
                border: `1px solid ${suggestionMessage.type === 'success' ? '#bbf7d0' : '#e2e8f0'}`,
                color: suggestionMessage.type === 'success' ? '#166534' : '#64748b',
              }}
            >
              {suggestionMessage.text}
            </div>
          )}
        </div>

        {/* Impact, Urgency, Affected Users Grid */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '16px', marginBottom: '24px' }}>
          <div>
            <label style={labelStyle}>Impact *</label>
            <select value={impact} onChange={(e) => setImpact(e.target.value)} style={inputStyle}>
              {Object.values(IMPACT).map((v) => (
                <option key={v} value={v}>
                  {v}
                </option>
              ))}
            </select>
            <span style={helpTextStyle}>Degree of damage / disruption</span>
          </div>

          <div>
            <label style={labelStyle}>Urgency *</label>
            <select value={urgency} onChange={(e) => setUrgency(e.target.value)} style={inputStyle}>
              {Object.values(URGENCY).map((v) => (
                <option key={v} value={v}>
                  {v}
                </option>
              ))}
            </select>
            <span style={helpTextStyle}>Speed of resolution needed</span>
          </div>

          <div>
            <label style={labelStyle}>Affected Users *</label>
            <input
              type="number"
              min={1}
              required
              value={affectedUsers}
              onChange={(e) => setAffectedUsers(Math.max(1, parseInt(e.target.value) || 1))}
              style={inputStyle}
            />
            <span style={helpTextStyle}>Number of impacted users</span>
          </div>
        </div>

        {/* Priority Calculation Engine Live Preview (§K) */}
        <div
          style={{
            padding: '16px',
            backgroundColor: '#f8fafc',
            borderRadius: '8px',
            border: '1px solid #e2e8f0',
            marginBottom: '28px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div>
            <div style={{ fontSize: '12px', fontWeight: '700', color: '#64748b', textTransform: 'uppercase' }}>
              Automatic Priority Calculation Engine
            </div>
            <div style={{ fontSize: '13px', color: '#475569', marginTop: '4px' }}>
              Formula: Impact × Urgency × Criticality + User Bump = <strong>{liveScore} pts</strong>
            </div>
          </div>
          <div>
            <PriorityBadge priority={livePriority} score={liveScore} showScore />
          </div>
        </div>

        {/* Submit Actions */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
          <button
            type="button"
            onClick={() => navigate(-1)}
            style={{
              padding: '10px 20px',
              borderRadius: '8px',
              border: '1px solid #cbd5e1',
              backgroundColor: '#fff',
              fontWeight: '600',
              fontSize: '14px',
              color: '#475569',
              cursor: 'pointer',
            }}
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={submitting}
            style={{
              padding: '10px 24px',
              borderRadius: '8px',
              border: 'none',
              backgroundColor: '#2563eb',
              color: '#fff',
              fontWeight: '700',
              fontSize: '14px',
              cursor: submitting ? 'not-allowed' : 'pointer',
              opacity: submitting ? 0.7 : 1,
            }}
          >
            {submitting ? 'Creating Incident...' : 'Submit Incident Report'}
          </button>
        </div>
      </form>
    </div>
  );
}

const formCardStyle = {
  backgroundColor: '#ffffff',
  borderRadius: '12px',
  padding: '28px',
  border: '1px solid #e2e8f0',
  boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.05)',
};

const labelStyle = {
  display: 'block',
  fontSize: '13px',
  fontWeight: '700',
  color: '#1e293b',
  marginBottom: '6px',
};

const inputStyle = {
  width: '100%',
  padding: '10px 14px',
  borderRadius: '8px',
  border: '1px solid #cbd5e1',
  fontSize: '14px',
  boxSizing: 'border-box',
  outline: 'none',
  fontFamily: 'inherit',
  backgroundColor: '#fff',
};

const helpTextStyle = {
  display: 'block',
  fontSize: '11px',
  color: '#94a3b8',
  marginTop: '4px',
};
