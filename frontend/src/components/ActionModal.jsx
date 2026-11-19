import { useState, useEffect } from 'react';
import { incidentApi } from '../api/apiClient';

export default function ActionModal({
  isOpen,
  title,
  description,
  actionLabel = 'Submit',
  actionType = 'text', // 'text' | 'feedback' | 'technician'
  technicians = [],
  incidentId = null,
  onConfirm,
  onClose,
}) {
  const [text, setText] = useState('');
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');
  const [selectedTech, setSelectedTech] = useState(technicians[0]?.id || '');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Smart recommendations state
  const [recommendations, setRecommendations] = useState([]);
  const [recLoading, setRecLoading] = useState(false);

  useEffect(() => {
    let ignore = false;
    async function loadRecs() {
      if (actionType === 'technician' && incidentId && isOpen) {
        setRecLoading(true);
        try {
          const recs = await incidentApi.getRecommendations(incidentId);
          if (!ignore && recs && recs.length > 0) {
            setRecommendations(recs);
            // Default select top recommendation if available
            setSelectedTech(recs[0].technicianId);
          }
        } catch {
          // Non-blocking: manual selection remains possible
          if (!ignore) setRecommendations([]);
        } finally {
          if (!ignore) setRecLoading(false);
        }
      }
    }
    loadRecs();
    return () => {
      ignore = true;
    };
  }, [actionType, incidentId, isOpen]);

  if (!isOpen) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      if (actionType === 'text') {
        if (!text.trim()) {
          throw new Error('Please enter text before submitting');
        }
        await onConfirm(text);
      } else if (actionType === 'feedback') {
        await onConfirm(rating, comment);
      } else if (actionType === 'technician') {
        if (!selectedTech) {
          throw new Error('Please select a technician');
        }
        await onConfirm(selectedTech);
      }
      onClose();
    } catch (err) {
      setError(err.message || 'Action failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.6)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
        backdropFilter: 'blur(2px)',
      }}
    >
      <div
        style={{
          backgroundColor: '#fff',
          borderRadius: '12px',
          width: actionType === 'technician' ? '560px' : '500px',
          maxWidth: '94%',
          maxHeight: '90vh',
          overflowY: 'auto',
          padding: '24px',
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
          <div>
            <h3 style={{ margin: 0, fontSize: '18px', fontWeight: '700', color: '#0f172a' }}>{title}</h3>
            {description && <p style={{ margin: '4px 0 0', fontSize: '13px', color: '#64748b' }}>{description}</p>}
          </div>
          <button
            onClick={onClose}
            style={{ border: 'none', background: 'transparent', cursor: 'pointer', fontSize: '18px', color: '#94a3b8' }}
          >
            ✕
          </button>
        </div>

        {error && (
          <div
            style={{
              padding: '10px 14px',
              backgroundColor: '#fee2e2',
              border: '1px solid #fca5a5',
              borderRadius: '6px',
              color: '#991b1b',
              fontSize: '13px',
              marginBottom: '16px',
            }}
          >
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          {actionType === 'text' && (
            <textarea
              rows={5}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Enter details..."
              style={{
                width: '100%',
                padding: '12px',
                borderRadius: '8px',
                border: '1px solid #cbd5e1',
                fontSize: '14px',
                boxSizing: 'border-box',
                outline: 'none',
                fontFamily: 'inherit',
              }}
              required
            />
          )}

          {actionType === 'technician' && (
            <div>
              {/* Smart Recommendations Section (Gate B2 / B5) */}
              <div style={{ marginBottom: '18px' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px' }}>
                  <label style={{ fontSize: '13px', fontWeight: '700', color: '#1e293b', display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span>🎯</span> Smart Technician Recommendations
                  </label>
                  {recLoading && (
                    <span style={{ fontSize: '11px', color: '#2563eb', fontWeight: '500' }}>
                      Analyzing skill & load...
                    </span>
                  )}
                </div>

                {recommendations.length > 0 ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
                    {recommendations.map((rec, index) => {
                      const isSelected = selectedTech === rec.technicianId;
                      return (
                        <div
                          key={rec.technicianId}
                          data-testid={`rec-card-${rec.technicianId}`}
                          onClick={() => setSelectedTech(rec.technicianId)}
                          style={{
                            padding: '12px 14px',
                            borderRadius: '8px',
                            border: isSelected ? '2px solid #2563eb' : '1px solid #e2e8f0',
                            backgroundColor: isSelected ? '#eff6ff' : '#f8fafc',
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            transition: 'all 0.15s ease',
                          }}
                        >
                          <div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                              <span
                                style={{
                                  fontSize: '11px',
                                  fontWeight: '700',
                                  padding: '2px 6px',
                                  borderRadius: '4px',
                                  backgroundColor: index === 0 ? '#dbeafe' : '#f1f5f9',
                                  color: index === 0 ? '#1e40af' : '#475569',
                                }}
                              >
                                #{index + 1} {index === 0 ? 'Best Fit' : ''}
                              </span>
                              <strong style={{ fontSize: '14px', color: '#0f172a' }}>{rec.name}</strong>
                              <span style={{ fontSize: '12px', color: '#64748b' }}>({rec.technicianId})</span>
                              <span
                                style={{
                                  fontSize: '11px',
                                  fontWeight: '600',
                                  padding: '2px 6px',
                                  borderRadius: '4px',
                                  backgroundColor: '#fef3c7',
                                  color: '#b45309',
                                }}
                              >
                                Score: {typeof rec.score === 'number' ? rec.score.toFixed(1) : rec.score}
                              </span>
                            </div>
                            <div style={{ fontSize: '12px', color: '#475569', marginBottom: '2px' }}>
                              {rec.reason}
                            </div>
                            <div style={{ fontSize: '11px', color: '#64748b', display: 'flex', gap: '12px' }}>
                              <span>Workload: <strong>{rec.activeWorkload}</strong> / {rec.maxCapacity} active</span>
                              {rec.yearsOfExperience != null && (
                                <span>Experience: <strong>{rec.yearsOfExperience}y</strong></span>
                              )}
                            </div>
                          </div>
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              setSelectedTech(rec.technicianId);
                            }}
                            style={{
                              padding: '6px 12px',
                              borderRadius: '6px',
                              border: isSelected ? '1px solid #2563eb' : '1px solid #cbd5e1',
                              backgroundColor: isSelected ? '#2563eb' : '#fff',
                              color: isSelected ? '#fff' : '#334155',
                              fontSize: '12px',
                              fontWeight: '600',
                              cursor: 'pointer',
                            }}
                          >
                            {isSelected ? '✓ Selected' : 'Select'}
                          </button>
                        </div>
                      );
                    })}
                  </div>
                ) : !recLoading ? (
                  <div
                    style={{
                      padding: '10px 12px',
                      borderRadius: '6px',
                      backgroundColor: '#f1f5f9',
                      fontSize: '12px',
                      color: '#64748b',
                      marginBottom: '12px',
                    }}
                  >
                    No automated technician recommendations available for this incident. Please choose manually below.
                  </div>
                ) : null}
              </div>

              {/* Manual Selection Fallback */}
              <div>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: '600', marginBottom: '6px', color: '#475569' }}>
                  Or select technician manually:
                </label>
                <select
                  value={selectedTech}
                  onChange={(e) => setSelectedTech(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '10px 12px',
                    borderRadius: '8px',
                    border: '1px solid #cbd5e1',
                    fontSize: '14px',
                    backgroundColor: '#fff',
                  }}
                >
                  {technicians.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.name} ({t.id}) — {t.available ? 'Available' : 'Unavailable'}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}

          {actionType === 'feedback' && (
            <div>
              <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', marginBottom: '8px', color: '#334155' }}>
                Rating (1 to 5 Stars):
              </label>
              <div style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
                {[1, 2, 3, 4, 5].map((star) => (
                  <button
                    key={star}
                    type="button"
                    onClick={() => setRating(star)}
                    style={{
                      padding: '8px 14px',
                      borderRadius: '6px',
                      border: rating === star ? '2px solid #f59e0b' : '1px solid #cbd5e1',
                      backgroundColor: rating >= star ? '#fef3c7' : '#fff',
                      color: rating >= star ? '#b45309' : '#64748b',
                      fontSize: '16px',
                      fontWeight: '700',
                      cursor: 'pointer',
                    }}
                  >
                    ★ {star}
                  </button>
                ))}
              </div>

              <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', marginBottom: '6px', color: '#334155' }}>
                Optional Feedback Comments:
              </label>
              <textarea
                rows={3}
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                placeholder="Share your experience..."
                style={{
                  width: '100%',
                  padding: '10px 12px',
                  borderRadius: '8px',
                  border: '1px solid #cbd5e1',
                  fontSize: '14px',
                  boxSizing: 'border-box',
                }}
              />
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              style={{
                padding: '8px 16px',
                borderRadius: '6px',
                border: '1px solid #cbd5e1',
                background: '#fff',
                fontSize: '13px',
                fontWeight: '600',
                color: '#475569',
                cursor: 'pointer',
              }}
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading}
              style={{
                padding: '8px 18px',
                borderRadius: '6px',
                border: 'none',
                background: '#2563eb',
                color: '#fff',
                fontSize: '13px',
                fontWeight: '600',
                cursor: loading ? 'not-allowed' : 'pointer',
                opacity: loading ? 0.7 : 1,
              }}
            >
              {loading ? 'Processing...' : actionLabel}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
