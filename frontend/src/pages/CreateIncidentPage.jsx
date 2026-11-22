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

  // Duplicate detection UI state (Gate B7)
  const [duplicateCandidates, setDuplicateCandidates] = useState([]);
  const [checkingDuplicates, setCheckingDuplicates] = useState(false);
  const [duplicateChecked, setDuplicateChecked] = useState(false);
  const [duplicatesDismissed, setDuplicatesDismissed] = useState(false);

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

  // Duplicate detection hook (Gate B7)
  const handleCheckDuplicates = async (customTitle) => {
    const titleToCheck = customTitle !== undefined ? customTitle : title;
    if (!titleToCheck || titleToCheck.trim().length < 3) {
      setDuplicateCandidates([]);
      setDuplicateChecked(false);
      return;
    }
    try {
      setCheckingDuplicates(true);
      const res = await incidentApi.checkDuplicates({
        title: titleToCheck.trim(),
        description: description.trim(),
        serviceId: serviceId || null,
        categoryId: categoryId || null,
      });
      if (res && res.candidates) {
        setDuplicateCandidates(res.candidates);
        setDuplicatesDismissed(false);
      } else {
        setDuplicateCandidates([]);
      }
      setDuplicateChecked(true);
    } catch (err) {
      console.error('Error checking duplicates', err);
    } finally {
      setCheckingDuplicates(false);
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

      {currentUser?.role && currentUser.role !== 'EMPLOYEE' && (
        <div
          data-testid="non-employee-warning"
          style={{
            padding: '14px 18px',
            backgroundColor: '#fffbeb',
            border: '1px solid #fde68a',
            borderRadius: '8px',
            color: '#92400e',
            fontSize: '13px',
            marginBottom: '20px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div>
            <strong>⚠️ Incident reporting is intended for Employees only.</strong>
            <div style={{ fontSize: '12px', marginTop: '2px', color: '#b45309' }}>
              Your account is an active <strong>{currentUser.role}</strong> (supervisory / technical staff).
            </div>
          </div>
          <button
            type="button"
            onClick={() => navigate(-1)}
            style={{
              padding: '6px 12px',
              backgroundColor: '#92400e',
              color: '#fff',
              border: 'none',
              borderRadius: '6px',
              fontWeight: '600',
              fontSize: '12px',
              cursor: 'pointer',
            }}
          >
            Return
          </button>
        </div>
      )}

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
        {/* Title & Duplicate Detection (Gate B7) */}
        <div style={{ marginBottom: '20px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
            <label style={{ ...labelStyle, marginBottom: 0 }}>Incident Title *</label>
            <button
              type="button"
              onClick={() => handleCheckDuplicates()}
              disabled={checkingDuplicates || !title.trim()}
              style={{
                background: '#fef3c7',
                color: '#b45309',
                border: '1px solid #fde68a',
                borderRadius: '6px',
                padding: '4px 10px',
                fontSize: '12px',
                fontWeight: '600',
                cursor: 'pointer',
              }}
            >
              {checkingDuplicates ? 'Checking Duplicates...' : '🔍 Check Duplicates'}
            </button>
          </div>
          <input
            type="text"
            required
            maxLength={200}
            value={title}
            onChange={(e) => {
              setTitle(e.target.value);
              if (duplicateChecked) setDuplicateChecked(false);
            }}
            onBlur={() => {
              if (title.trim().length >= 3 && !duplicateChecked) {
                handleCheckDuplicates();
              }
            }}
            placeholder="e.g., Payment gateway 504 timeout during checkout"
            style={inputStyle}
          />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={helpTextStyle}>Brief, descriptive summary of the disruption (1..200 chars).</span>
            {duplicateChecked && duplicateCandidates.length === 0 && (
              <span style={{ fontSize: '11px', color: '#16a34a', fontWeight: '600', marginTop: '4px' }}>
                ✓ No active duplicates detected
              </span>
            )}
          </div>

          {/* Advisory Duplicate Candidates Banner (Gate B7) */}
          {duplicateCandidates.length > 0 && !duplicatesDismissed && (
            <div
              data-testid="duplicate-advisory-banner"
              style={{
                marginTop: '12px',
                padding: '14px 16px',
                backgroundColor: '#fffbeb',
                border: '1px solid #fde68a',
                borderRadius: '8px',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <h4 style={{ margin: '0 0 4px 0', color: '#92400e', fontSize: '13px', fontWeight: '700' }}>
                    ⚠️ Potential Duplicate Incidents Detected ({duplicateCandidates.length})
                  </h4>
                  <p style={{ margin: 0, fontSize: '12px', color: '#b45309' }}>
                    Similar active incidents already exist. Review before submitting to avoid redundant work:
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setDuplicatesDismissed(true)}
                  style={{
                    background: 'transparent',
                    border: 'none',
                    color: '#b45309',
                    fontSize: '12px',
                    fontWeight: '600',
                    cursor: 'pointer',
                    textDecoration: 'underline',
                  }}
                >
                  Dismiss
                </button>
              </div>

              <div style={{ marginTop: '10px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {duplicateCandidates.map((candidate) => (
                  <div
                    key={candidate.id}
                    data-testid="duplicate-candidate-card"
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      padding: '8px 12px',
                      backgroundColor: '#ffffff',
                      borderRadius: '6px',
                      border: '1px solid #fef3c7',
                    }}
                  >
                    <div style={{ flex: 1, marginRight: '12px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span
                          style={{
                            fontFamily: 'monospace',
                            fontWeight: '700',
                            fontSize: '11px',
                            color: '#1e293b',
                            backgroundColor: '#f1f5f9',
                            padding: '2px 6px',
                            borderRadius: '4px',
                          }}
                        >
                          {candidate.id}
                        </span>
                        <span style={{ fontSize: '13px', fontWeight: '600', color: '#0f172a' }}>
                          {candidate.title}
                        </span>
                        <span
                          style={{
                            fontSize: '11px',
                            fontWeight: '600',
                            padding: '1px 6px',
                            borderRadius: '4px',
                            backgroundColor: '#e0f2fe',
                            color: '#0369a1',
                          }}
                        >
                          {candidate.status}
                        </span>
                      </div>
                      <div style={{ fontSize: '11px', color: '#64748b', marginTop: '2px' }}>
                        {candidate.matchReason}
                      </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span
                        style={{
                          fontSize: '11px',
                          fontWeight: '700',
                          padding: '2px 8px',
                          borderRadius: '9999px',
                          backgroundColor: candidate.score >= 0.8 ? '#fef2f2' : '#fefce8',
                          color: candidate.score >= 0.8 ? '#b91c1c' : '#854d0e',
                          border: `1px solid ${candidate.score >= 0.8 ? '#fecaca' : '#fef08a'}`,
                        }}
                      >
                        {Math.round(candidate.score * 100)}% Match
                      </span>
                      <button
                        type="button"
                        onClick={() => window.open(`/incidents/${candidate.id}`, '_blank')}
                        style={{
                          padding: '3px 8px',
                          fontSize: '11px',
                          fontWeight: '600',
                          backgroundColor: '#eff6ff',
                          color: '#1d4ed8',
                          border: '1px solid #bfdbfe',
                          borderRadius: '5px',
                          cursor: 'pointer',
                        }}
                      >
                        View Ticket ↗
                      </button>
                    </div>
                  </div>
                ))}
              </div>

              <div style={{ marginTop: '10px', display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                <button
                  type="button"
                  onClick={() => setDuplicatesDismissed(true)}
                  style={{
                    padding: '5px 12px',
                    fontSize: '11px',
                    fontWeight: '600',
                    backgroundColor: '#fef3c7',
                    color: '#92400e',
                    border: '1px solid #fde68a',
                    borderRadius: '5px',
                    cursor: 'pointer',
                  }}
                >
                  Continue Submitting Anyway
                </button>
              </div>
            </div>
          )}
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
