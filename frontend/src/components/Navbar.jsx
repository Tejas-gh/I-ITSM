import { NavLink, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { incidentApi, USE_MOCK } from '../api/apiClient';
import NotificationBell from './NotificationBell';

export default function Navbar() {
  const { currentUser, availableUsers, selectUser, logout, isEmployee, isAdmin } = useAuth();

  const handleResetData = () => {
    if (window.confirm('Reset all mock incident data back to original seed?')) {
      incidentApi.resetMockData();
      window.location.reload();
    }
  };

  const navLinkStyle = ({ isActive }) => ({
    padding: '8px 14px',
    borderRadius: '6px',
    fontSize: '14px',
    fontWeight: '500',
    textDecoration: 'none',
    color: isActive ? '#1e293b' : '#64748b',
    backgroundColor: isActive ? '#f1f5f9' : 'transparent',
    transition: 'all 0.15s ease',
  });

  return (
    <header
      style={{
        borderBottom: '1px solid #e2e8f0',
        backgroundColor: '#ffffff',
        position: 'sticky',
        top: 0,
        zIndex: 100,
        boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.05)',
      }}
    >
      <div
        style={{
          maxWidth: '1200px',
          margin: '0 auto',
          padding: '12px 24px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        {/* Left: Brand & Nav links */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '32px' }}>
          <Link
            to="/"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
              textDecoration: 'none',
              color: '#0f172a',
            }}
          >
            <div
              style={{
                width: '36px',
                height: '36px',
                borderRadius: '8px',
                background: 'linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%)',
                color: '#fff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: '800',
                fontSize: '18px',
                letterSpacing: '-0.5px',
              }}
            >
              I
            </div>
            <div>
              <div style={{ fontWeight: '800', fontSize: '18px', lineHeight: '1.1' }}>I-ITSM</div>
              <div style={{ fontSize: '10px', color: '#64748b', letterSpacing: '0.5px', fontWeight: '600' }}>
                SERVICE MANAGEMENT
              </div>
            </div>
          </Link>

          <nav style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <NavLink to="/" style={navLinkStyle}>
              Dashboard
            </NavLink>
            <NavLink to="/incidents" style={navLinkStyle}>
              Incidents
            </NavLink>
            {(USE_MOCK || isEmployee) && (
              <NavLink to="/incidents/new" style={navLinkStyle}>
                + Report Incident
              </NavLink>
            )}
            {(USE_MOCK || isAdmin) && (
              <NavLink to="/technicians" style={navLinkStyle}>
                Technicians
              </NavLink>
            )}
          </nav>
        </div>

        {/* Right: Active Role Switcher, Notifications & Tools */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          {!USE_MOCK && <NotificationBell />}

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              padding: '6px 12px',
              borderRadius: '8px',
              backgroundColor: '#f8fafc',
              border: '1px solid #e2e8f0',
            }}
          >
            <span style={{ fontSize: '12px', color: '#64748b', fontWeight: '500' }}>Logged in as:</span>
            {USE_MOCK ? (
              <select
                value={currentUser.id}
                onChange={(e) => selectUser(e.target.value)}
                style={{
                  border: 'none',
                  background: 'transparent',
                  fontWeight: '600',
                  fontSize: '13px',
                  color: '#0f172a',
                  cursor: 'pointer',
                  outline: 'none',
                }}
              >
                {availableUsers.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.name} ({u.role})
                  </option>
                ))}
              </select>
            ) : (
              <span style={{ fontWeight: '600', fontSize: '13px', color: '#0f172a' }}>
                {currentUser.id} ({currentUser.role})
              </span>
            )}
          </div>

          {USE_MOCK ? (
            <button
              onClick={handleResetData}
              title="Reset Mock Store"
              style={{
                padding: '6px 12px',
                fontSize: '12px',
                fontWeight: '500',
                borderRadius: '6px',
                border: '1px solid #cbd5e1',
                backgroundColor: '#ffffff',
                color: '#475569',
                cursor: 'pointer',
              }}
            >
              ↺ Reset Seed
            </button>
          ) : (
            <button
              onClick={logout}
              style={{
                padding: '6px 12px',
                fontSize: '12px',
                fontWeight: '500',
                borderRadius: '6px',
                border: '1px solid #cbd5e1',
                backgroundColor: '#ffffff',
                color: '#475569',
                cursor: 'pointer',
              }}
            >
              Sign out
            </button>
          )}
        </div>
      </div>
    </header>
  );
}
