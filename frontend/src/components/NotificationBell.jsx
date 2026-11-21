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

function timestamp(notification) {
  const value = new Date(notification.createdAt || 0).getTime();
  return Number.isFinite(value) ? value : 0;
}

export default function NotificationBell() {
  const { currentUser } = useAuth();
  const role = String(currentUser?.role || '').toUpperCase();
  const navigate = useNavigate();
  const [notifications, setNotifications] = useState([]);
  const [isOpen, setIsOpen] = useState(false);
  const [filter, setFilter] = useState('all');
  const [loading, setLoading] = useState(false);
  const [markingAll, setMarkingAll] = useState(false);
  const containerRef = useRef(null);

  const fetchNotifications = useCallback(async () => {
    if (USE_MOCK || !currentUser) return;
    try {
      const list = await notificationApi.getNotifications();
      setNotifications(Array.isArray(list) ? list : []);
    } catch {
      // Keep the existing notification list during background failures.
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
        if (!ignore) setNotifications(Array.isArray(list) ? list : []);
      } catch {
        // Ignore background polling failures.
      }
    };

    load();
    const interval = setInterval(load, 30000);
    return () => {
      ignore = true;
      clearInterval(interval);
    };
  }, [currentUser]);

  useEffect(() => {
    const handleOutsideClick = (event) => {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    };

    if (isOpen) document.addEventListener('mousedown', handleOutsideClick);
    return () => document.removeEventListener('mousedown', handleOutsideClick);
  }, [isOpen]);

  const handleMarkAsRead = async (id, event) => {
    event?.stopPropagation();
    try {
      await notificationApi.markAsRead(id);
      setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
    } catch (err) {
      console.error('Failed to mark notification read', err);
    }
  };

  const handleMarkAllAsRead = async () => {
    const unread = notifications.filter((n) => !n.read);
    if (!unread.length || markingAll) return;

    setMarkingAll(true);
    try {
      await Promise.all(unread.map((n) => notificationApi.markAsRead(n.id)));
      setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
    } catch (err) {
      console.error('Failed to mark all notifications read', err);
      await fetchNotifications();
    } finally {
      setMarkingAll(false);
    }
  };

  const handleNotificationClick = async (notification) => {
    if (!notification.read) await handleMarkAsRead(notification.id);
    if (notification.incidentId) {
      setIsOpen(false);
      navigate(`/incidents/${notification.incidentId}`);
    }
  };

  const getNotificationMeta = (notification) => {
    const raw = String(
      notification.type ?? notification.kind ?? notification.category ?? notification.eventType ?? ''
    ).toUpperCase();
    const text = `${raw} ${notification.title ?? ''} ${notification.body ?? ''}`.toUpperCase();

    if (text.includes('SLA') || text.includes('VIOLATION') || text.includes('BREACH') || text.includes('AT-RISK') || text.includes('DEADLINE')) {
      return { label: 'SLA Alert', icon: '◷', bg: '#fff7ed', color: '#c2410c', actionable: false };
    }

    if (text.includes('ESCALAT')) {
      const escalationRole = ['TEAM_LEAD', 'ADMINISTRATOR', 'IT_MANAGER'].includes(role);
      return escalationRole
        ? { label: 'Escalation', icon: '↗', bg: '#fef2f2', color: '#b91c1c', actionable: true }
        : { label: 'Incident Update', icon: '✓', bg: '#f0fdf4', color: '#15803d', actionable: false };
    }

    // Employee actions: respond to an information request, verify a resolution,
    // or provide requested feedback. These are the employee's genuine next steps.
    if (role === 'EMPLOYEE') {
      if (
        text.includes('INFO_REQUEST') ||
        text.includes('INFORMATION REQUEST') ||
        text.includes('INFO REQUEST') ||
        text.includes('ADDITIONAL INFORMATION') ||
        text.includes('INFORMATION REQUIRED')
      ) {
        return { label: 'Action Required', icon: '!', bg: '#eff6ff', color: '#1d4ed8', actionable: true };
      }

      if (
        text.includes('VERIFY') ||
        text.includes('VERIFICATION') ||
        text.includes('ACCEPT RESOLUTION') ||
        text.includes('RESOLUTION READY') ||
        text.includes('RESOLUTION SUBMITTED')
      ) {
        return { label: 'Action Required', icon: '!', bg: '#eff6ff', color: '#1d4ed8', actionable: true };
      }

      if (text.includes('FEEDBACK')) {
        return { label: 'Action Required', icon: '!', bg: '#eff6ff', color: '#1d4ed8', actionable: true };
      }
    }

    // Technician / Team Lead: assignment and rejected resolution require work.
    if (role === 'TECHNICIAN' || role === 'TEAM_LEAD') {
      if (text.includes('ASSIGN')) {
        return { label: 'Action Required', icon: '!', bg: '#eff6ff', color: '#1d4ed8', actionable: true };
      }

      if (text.includes('REJECT')) {
        return { label: 'Action Required', icon: '!', bg: '#eff6ff', color: '#1d4ed8', actionable: true };
      }

      if (
        text.includes('INFO_RESPONSE') ||
        text.includes('INFORMATION RECEIVED') ||
        text.includes('ADDITIONAL INFORMATION RECEIVED')
      ) {
        return { label: 'Action Required', icon: '!', bg: '#eff6ff', color: '#1d4ed8', actionable: true };
      }
    }

    if (
      text.includes('ASSIGN') ||
      text.includes('ACKNOWLEDG') ||
      text.includes('RESOLV') ||
      text.includes('CLOS') ||
      text.includes('REOPEN') ||
      text.includes('INCIDENT')
    ) {
      return { label: 'Incident Update', icon: '✓', bg: '#f0fdf4', color: '#15803d', actionable: false };
    }

    return { label: 'Information', icon: 'i', bg: '#f8fafc', color: '#475569', actionable: false };
  };

  const getDisplayNotification = (notification) => {
    if (role !== 'TECHNICIAN' && role !== 'TEAM_LEAD') return notification;

    const raw = String(
      notification.type ?? notification.kind ?? notification.category ?? notification.eventType ?? ''
    ).toUpperCase();
    const text = `${raw} ${notification.title ?? ''} ${notification.body ?? ''}`.toUpperCase();

    // Assignment notifications are delivered to the assignee. For a technician/team lead,
    // make the message explicit so there is no ambiguity about whose work it is.
    if (text.includes('ASSIGN') && !text.includes('REASSIGN')) {
      return {
        ...notification,
        title: 'Incident Assigned to You',
        body: notification.incidentId
          ? `Incident ${notification.incidentId} has been assigned to you.`
          : 'An incident has been assigned to you.',
      };
    }

    return notification;
  };

  const sortedNotifications = [...notifications].sort((a, b) => timestamp(b) - timestamp(a));
  const unreadCount = sortedNotifications.filter((n) => !n.read).length;

  // If a newer notification exists for the same incident, an older action is
  // no longer treated as the current next step. This prevents old assignments
  // from keeping the Action count alive after the incident has progressed/closed.
  const latestByIncident = new Map();
  sortedNotifications.forEach((notification) => {
    if (notification.incidentId && !latestByIncident.has(notification.incidentId)) {
      latestByIncident.set(notification.incidentId, notification);
    }
  });

  const isCurrentAction = (notification) => {
    const meta = getNotificationMeta(notification);
    if (!meta.actionable || notification.read) return false;
    if (!notification.incidentId) return true;
    return latestByIncident.get(notification.incidentId)?.id === notification.id;
  };

  const currentActions = sortedNotifications.filter(isCurrentAction);
  const currentSlas = sortedNotifications.filter((notification) => {
    if (notification.read || getNotificationMeta(notification).label !== 'SLA Alert') return false;
    if (!notification.incidentId) return true;
    return latestByIncident.get(notification.incidentId)?.id === notification.id;
  });

  const notificationCounts = {
    action: currentActions.length,
    sla: currentSlas.length,
  };

  const visibleNotifications = filter === 'unread'
    ? sortedNotifications.filter((n) => !n.read)
    : sortedNotifications;

  const currentActionIds = new Set(currentActions.map((n) => n.id));
  const currentSlaIds = new Set(currentSlas.map((n) => n.id));

  const needsAttention = visibleNotifications.filter((n) => currentActionIds.has(n.id));
  const slaNotifications = visibleNotifications.filter((n) => currentSlaIds.has(n.id));
  const recentNotifications = visibleNotifications.filter(
    (n) => !currentActionIds.has(n.id) && !currentSlaIds.has(n.id)
  );

  const roleLabel = {
    EMPLOYEE: 'Your incidents · actions waiting for you',
    TECHNICIAN: 'Your work · actions waiting for you',
    TEAM_LEAD: 'Team work · actions and escalations',
    ADMINISTRATOR: 'Operations · escalations and alerts',
    IT_MANAGER: 'Management · escalations and alerts',
  }[role] || 'Updates and actions related to your work';

  const sections = [
    { key: 'attention', title: 'NEEDS YOUR ACTION', items: needsAttention },
    { key: 'sla', title: 'SLA ALERTS', items: slaNotifications },
    { key: 'recent', title: 'RECENT ACTIVITY', items: recentNotifications },
  ].filter((section) => section.items.length > 0);

  return (
    <div ref={containerRef} style={{ position: 'relative' }}>
      <button
        type="button"
        onClick={() => {
          setIsOpen((prev) => !prev);
          if (!isOpen) fetchNotifications();
        }}
        aria-label="Notifications"
        aria-expanded={isOpen}
        style={{
          position: 'relative',
          padding: '9px',
          borderRadius: '9px',
          border: '1px solid #dbe3ec',
          backgroundColor: isOpen ? '#eef4fb' : '#ffffff',
          color: '#334155',
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          transition: 'all 0.15s ease',
        }}
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>
        {unreadCount > 0 && (
          <span style={{
            position: 'absolute', top: '-4px', right: '-4px', backgroundColor: '#ef4444', color: '#ffffff',
            fontSize: '11px', fontWeight: '800', borderRadius: '9999px', minWidth: '18px', height: '18px',
            padding: '0 5px', display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: '0 1px 2px rgba(0, 0, 0, 0.12)',
          }}>
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div
          role="dialog"
          aria-label="Notification Center"
          style={{
            position: 'absolute',
            right: 0,
            top: 'calc(100% + 10px)',
            width: '430px',
            maxWidth: 'calc(100vw - 24px)',
            maxHeight: '590px',
            backgroundColor: '#ffffff',
            borderRadius: '14px',
            border: '1px solid #dbe3ec',
            boxShadow: '0 20px 45px -18px rgba(15, 23, 42, 0.28), 0 8px 18px -10px rgba(15, 23, 42, 0.18)',
            zIndex: 1000,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}
        >
          <div style={{ padding: '18px 18px 13px', borderBottom: '1px solid #e5eaf0', background: 'linear-gradient(135deg, #f8fbff 0%, #ffffff 72%)' }}>
            <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '14px' }}>
              <div style={{ minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span style={{ width: '30px', height: '30px', borderRadius: '9px', background: '#eaf2ff', color: '#2563eb', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: '15px' }} aria-hidden="true">🔔</span>
                  <div>
                    <div style={{ fontWeight: '800', fontSize: '18px', color: '#0f172a', letterSpacing: '-0.3px' }}>Notification Center</div>
                    <div style={{ fontSize: '12px', color: '#64748b', marginTop: '2px' }}>{roleLabel}</div>
                  </div>
                </div>
              </div>

              <button type="button" onClick={handleManualRefresh} aria-label="Refresh notifications" style={{ background: '#ffffff', border: '1px solid #dbe3ec', borderRadius: '9px', color: '#334155', fontSize: '12px', cursor: 'pointer', padding: '7px 10px', fontWeight: '700', flexShrink: 0 }}>
                {loading ? 'Refreshing…' : '↻ Refresh'}
              </button>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '8px', marginTop: '15px' }}>
              {[
                { label: 'Unread', value: unreadCount, icon: '●', active: unreadCount > 0 },
                { label: 'SLA', value: notificationCounts.sla, icon: '◷', active: notificationCounts.sla > 0 },
                { label: 'Needs Action', value: notificationCounts.action, icon: '!', active: notificationCounts.action > 0 },
              ].map((item) => (
                <div key={item.label} style={{ padding: '10px 11px', borderRadius: '9px', border: '1px solid #dbe3ec', background: item.active ? '#f8fbff' : '#ffffff' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: item.active ? '#334155' : '#94a3b8' }}>
                    <span style={{ fontSize: '10px' }}>{item.icon}</span>
                    <span style={{ fontSize: '11px', fontWeight: '750' }}>{item.label}</span>
                  </div>
                  <div style={{ fontSize: '19px', lineHeight: 1.1, fontWeight: '800', color: '#0f172a', marginTop: '4px' }}>{item.value}</div>
                </div>
              ))}
            </div>

            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '13px' }}>
              <div role="tablist" aria-label="Notification filter" style={{ display: 'flex', gap: '4px', background: '#f1f5f9', borderRadius: '9px', padding: '3px' }}>
                {['all', 'unread'].map((tab) => (
                  <button key={tab} type="button" role="tab" aria-selected={filter === tab} onClick={() => setFilter(tab)} style={{ border: 'none', borderRadius: '7px', padding: '7px 12px', background: filter === tab ? '#ffffff' : 'transparent', boxShadow: filter === tab ? '0 1px 3px rgba(15, 23, 42, 0.10)' : 'none', color: filter === tab ? '#0f172a' : '#64748b', fontSize: '12px', fontWeight: filter === tab ? '750' : '650', cursor: 'pointer' }}>
                    {tab === 'all' ? 'All' : `Unread${unreadCount ? ` (${unreadCount})` : ''}`}
                  </button>
                ))}
              </div>

              {unreadCount > 0 && (
                <button type="button" onClick={handleMarkAllAsRead} disabled={markingAll} style={{ border: 'none', background: 'transparent', color: '#2563eb', fontSize: '12px', fontWeight: '700', cursor: markingAll ? 'default' : 'pointer', padding: '5px 0', opacity: markingAll ? 0.6 : 1 }}>
                  {markingAll ? 'Marking…' : 'Mark all as read'}
                </button>
              )}
            </div>
          </div>

          <div style={{ overflowY: 'auto', flex: 1 }}>
            {visibleNotifications.length === 0 ? (
              <div style={{ padding: '48px 18px', textAlign: 'center' }}>
                <div style={{ width: '42px', height: '42px', borderRadius: '12px', background: '#f0fdf4', color: '#16a34a', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: '20px', marginBottom: '10px' }}>✓</div>
                <div style={{ color: '#334155', fontSize: '14px', fontWeight: '700' }}>{filter === 'unread' ? 'No unread notifications' : 'You’re all caught up'}</div>
                <div style={{ color: '#94a3b8', fontSize: '12px', marginTop: '4px' }}>{filter === 'unread' ? 'There is nothing waiting for you.' : 'New updates will appear here.'}</div>
              </div>
            ) : (
              sections.map((section) => (
                <div key={section.key}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '11px 16px 7px', fontSize: '10px', fontWeight: '850', letterSpacing: '0.8px', color: section.key === 'attention' ? '#475569' : section.key === 'sla' ? '#c2410c' : '#94a3b8', background: '#ffffff' }}>
                    <span>{section.title}</span>
                    <span style={{ fontSize: '10px', letterSpacing: '0', fontWeight: '750' }}>{section.items.length}</span>
                  </div>

                  {section.items.map((notification) => {
                    const meta = getNotificationMeta(notification);
                    const displayNotification = getDisplayNotification(notification);
                    return (
                      <div key={notification.id} onClick={() => handleNotificationClick(notification)} style={{ padding: '13px 16px', borderTop: '1px solid #f1f5f9', backgroundColor: notification.read ? '#ffffff' : '#f8fbff', cursor: notification.incidentId ? 'pointer' : 'default' }} onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = notification.read ? '#f8fafc' : '#eef6ff'; }} onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = notification.read ? '#ffffff' : '#f8fbff'; }}>
                        <div style={{ display: 'flex', alignItems: 'flex-start', gap: '11px' }}>
                          <span aria-hidden="true" style={{ width: '31px', height: '31px', flexShrink: 0, borderRadius: '9px', background: meta.bg, color: meta.color, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: '850', fontSize: '15px', border: `1px solid ${meta.color}22` }}>{meta.icon}</span>

                          <div style={{ minWidth: 0, flex: 1 }}>
                            <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '8px' }}>
                              <div style={{ minWidth: 0 }}>
                                <div style={{ fontWeight: notification.read ? '650' : '800', fontSize: '14px', color: '#0f172a', lineHeight: '1.35' }}>{displayNotification.title}</div>
                                <div style={{ marginTop: '5px', display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
                                  <span style={{ background: meta.bg, color: meta.color, borderRadius: '999px', padding: '3px 7px', fontSize: '10px', fontWeight: '750' }}>{meta.label}</span>
                                  {notification.incidentId && <span style={{ background: '#f1f5f9', color: '#334155', borderRadius: '5px', padding: '3px 7px', fontSize: '10px', fontWeight: '750' }}>{notification.incidentId}</span>}
                                </div>
                              </div>

                              {!notification.read && (
                                <button type="button" onClick={(e) => handleMarkAsRead(notification.id, e)} title="Mark as read" style={{ border: '1px solid #bfdbfe', background: '#eff6ff', color: '#2563eb', borderRadius: '7px', fontSize: '10px', cursor: 'pointer', fontWeight: '750', padding: '5px 7px', flexShrink: 0 }}>Read</button>
                              )}
                            </div>

                            <div style={{ fontSize: '12.5px', color: '#475569', marginTop: '7px', lineHeight: '1.5' }}>{displayNotification.body}</div>
                            <div style={{ fontSize: '11px', color: '#94a3b8', marginTop: '7px' }}>{formatTime(notification.createdAt)}{notification.incidentId && ' · View incident →'}</div>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
