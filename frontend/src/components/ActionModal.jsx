import { useState } from 'react';

export default function ActionModal({
  isOpen,
  title,
  description,
  actionLabel = 'Submit',
  actionType = 'text', // 'text' | 'feedback' | 'technician'
  technicians = [],
  onConfirm,
  onClose,
}) {
  const [text, setText] = useState('');
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');
  const [selectedTech, setSelectedTech] = useState(technicians[0]?.id || '');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

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
          width: '500px',
          maxWidth: '92%',
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
              <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', marginBottom: '6px', color: '#334155' }}>
                Select Assignee:
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
