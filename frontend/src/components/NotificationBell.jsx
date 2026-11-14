import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { notificationApi, USE_MOCK } from '../api/apiClient';
import { useAuth } from '../context/AuthContext';

function formatTime(isoString) {
  if (!isoString) return '';
  try {
    const date = new Date(isoString);
    const now = new Date();
    const diffSec = Math.floor((now - date) / 1000);
    if (diffSec < 60) return 'just now';
    const diffMin = Math.floor(diffSec / 60);
    if (diffMin < 60) return `${diffMin}m ago`;
    const diffHour = Math.floor(diffMin / 60);
    if (diffHour < 24) return `${diffHour}h ago`;
    return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  } catch {
    return '';
  }
}

export default function NotificationBell() {
  const { currentUser } = useAuth();
  const navigate = useNavigate();
  const [notifications, setNotifications] = useState([]);
  const [isOpen, setIsOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const containerRef = useRef(null);

  const fetchNotifications = useCallback(async () => {
    if (USE_MOCK || !currentUser) return;
    try {
      const list = await notificationApi.getNotifications();
      setNotifications(Array.isArray(list) ? list : []);
    } catch {
      // silently swallow in background polling
    }
  }, [currentUser]);

  const handleManualRefresh = async () => {
    setLoading(true);
    await fetchNotifications();
    setLoading(false);
  };

  useEffect(() => {
    let ignore = false;
    const load = async () => {
      if (USE_MOCK || !currentUser) return;
      try {
        const list = await notificationApi.getNotifications();
        if (!ignore) {
          setNotifications(Array.isArray(list) ? list : []);
        }
      } catch {
        // ignore
      }
    };
    load();
    const interval = setInterval(load, 30000);
    return () => {
      ignore = true;
      clearInterval(interval);
    };
  }, [currentUser]);

  // Click outside to close
  useEffect(() => {
    const handleOutsideClick = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setIsOpen(false);
      }
    };
    if (isOpen) {
      document.addEventListener('mousedown', handleOutsideClick);
    }
    return () => {
      document.removeEventListener('mousedown', handleOutsideClick);
    };
  }, [isOpen]);

  const handleMarkAsRead = async (id, e) => {
    e?.stopPropagation();
    try {
      await notificationApi.markAsRead(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true } : n))
      );
    } catch (err) {
      console.error('Failed to mark notification read', err);
    }
  };

  const handleNotificationClick = async (notif) => {
    if (!notif.read) {
      await handleMarkAsRead(notif.id);
    }
    if (notif.incidentId) {
      setIsOpen(false);
      navigate(`/incidents/${notif.incidentId}`);
    }
  };

  const unreadCount = notifications.filter((n) => !n.read).length;

  return (
    <div ref={containerRef} style={{ position: 'relative' }}>
      <button
        type="button"
        onClick={() => {
          setIsOpen((prev) => !prev);
          if (!isOpen) fetchNotifications();
        }}
        aria-label="Notifications"
        style={{
          position: 'relative',
          padding: '8px',
          borderRadius: '8px',
          border: '1px solid #e2e8f0',
          backgroundColor: isOpen ? '#f1f5f9' : '#ffffff',
          color: '#475569',
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          transition: 'all 0.15s ease',
        }}
      >
        {/* Bell SVG */}
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>

        {unreadCount > 0 && (
          <span
            style={{
              position: 'absolute',
              top: '-4px',
              right: '-4px',
              backgroundColor: '#ef4444',
              color: '#ffffff',
              fontSize: '10px',
              fontWeight: '700',
              borderRadius: '9999px',
              minWidth: '16px',
              height: '16px',
              padding: '0 4px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 1px 2px rgba(0, 0, 0, 0.1)',
            }}
          >
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {/* Dropdown panel */}
      {isOpen && (
        <div
          style={{
            position: 'absolute',
            right: 0,
            top: 'calc(100% + 8px)',
            width: '340px',
            maxHeight: '420px',
            backgroundColor: '#ffffff',
            borderRadius: '10px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
            zIndex: 1000,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}
        >
          {/* Header */}
          <div
            style={{
              padding: '12px 16px',
              borderBottom: '1px solid #f1f5f9',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              backgroundColor: '#f8fafc',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ fontWeight: '700', fontSize: '14px', color: '#0f172a' }}>Notifications</span>
              {unreadCount > 0 && (
                <span
                  style={{
                    backgroundColor: '#dbeafe',
                    color: '#1d4ed8',
                    fontSize: '11px',
                    fontWeight: '600',
                    padding: '2px 8px',
                    borderRadius: '12px',
                  }}
                >
                  {unreadCount} unread
                </span>
              )}
            </div>
            <button
              type="button"
              onClick={handleManualRefresh}
              style={{
                background: 'transparent',
                border: 'none',
                color: '#64748b',
                fontSize: '12px',
                cursor: 'pointer',
                fontWeight: '500',
              }}
            >
              {loading ? 'Refreshing…' : 'Refresh'}
            </button>
          </div>

          {/* List */}
          <div style={{ overflowY: 'auto', flex: 1 }}>
            {notifications.length === 0 ? (
              <div style={{ padding: '32px 16px', textAlign: 'center', color: '#94a3b8', fontSize: '13px' }}>
                No notifications right now
              </div>
            ) : (
              notifications.map((n) => (
                <div
                  key={n.id}
                  onClick={() => handleNotificationClick(n)}
                  style={{
                    padding: '12px 16px',
                    borderBottom: '1px solid #f1f5f9',
                    backgroundColor: n.read ? '#ffffff' : '#f0f9ff',
                    cursor: n.incidentId ? 'pointer' : 'default',
                    transition: 'background-color 0.15s ease',
                    position: 'relative',
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.backgroundColor = n.read ? '#f8fafc' : '#e0f2fe';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.backgroundColor = n.read ? '#ffffff' : '#f0f9ff';
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '8px' }}>
                    <div style={{ fontWeight: n.read ? '500' : '700', fontSize: '13px', color: '#0f172a' }}>
                      {n.title}
                    </div>
                    {!n.read && (
                      <button
                        type="button"
                        onClick={(e) => handleMarkAsRead(n.id, e)}
                        title="Mark as read"
                        style={{
                          background: 'transparent',
                          border: 'none',
                          color: '#2563eb',
                          fontSize: '11px',
                          cursor: 'pointer',
                          fontWeight: '600',
                          padding: 0,
                          flexShrink: 0,
                        }}
                      >
                        ✓ Mark read
                      </button>
                    )}
                  </div>
                  <div style={{ fontSize: '12px', color: '#475569', marginTop: '4px', lineHeight: '1.4' }}>
                    {n.body}
                  </div>
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '8px',
                      marginTop: '6px',
                      fontSize: '11px',
                      color: '#94a3b8',
                    }}
                  >
                    {n.incidentId && (
                      <span
                        style={{
                          backgroundColor: '#e2e8f0',
                          color: '#334155',
                          padding: '1px 6px',
                          borderRadius: '4px',
                          fontWeight: '600',
                          fontSize: '10px',
                        }}
                      >
                        {n.incidentId}
                      </span>
                    )}
                    <span>{formatTime(n.createdAt)}</span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
