import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { authApi } from '../api/apiClient';

const fieldStyle = {
  width: '100%',
  boxSizing: 'border-box',
  padding: '10px 12px',
  borderRadius: '6px',
  border: '1px solid #cbd5e1',
  fontSize: '14px',
  marginTop: '4px',
};

const labelStyle = { display: 'block', fontSize: '13px', fontWeight: '600', color: '#334155', marginBottom: '14px' };

const EMPTY_REGISTRATION = { id: '', name: '', email: '', department: '', password: '' };

/** Sign-in and employee self-registration (POST /api/auth/login, /api/auth/register). */
export default function LoginPage() {
  const { login } = useAuth();
  const [mode, setMode] = useState('login');
  const [id, setId] = useState('');
  const [password, setPassword] = useState('');
  const [form, setForm] = useState(EMPTY_REGISTRATION);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const switchMode = (next) => {
    setMode(next);
    setError(null);
    setNotice(null);
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(id.trim(), password);
    } catch (err) {
      setError(err.message || 'Sign-in failed');
      setBusy(false);
    }
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const created = await authApi.register({
        id: form.id.trim(),
        name: form.name.trim(),
        email: form.email.trim(),
        department: form.department.trim(),
        password: form.password,
      });
      setId(created.id);
      setPassword('');
      setForm(EMPTY_REGISTRATION);
      setMode('login');
      setNotice(`Account ${created.id} created. Sign in to continue.`);
    } catch (err) {
      setError(err.message || 'Registration failed');
    } finally {
      setBusy(false);
    }
  };

  const setField = (name) => (e) => setForm((prev) => ({ ...prev, [name]: e.target.value }));

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: '#f8fafc', padding: '24px' }}>
      <div style={{ width: '100%', maxWidth: '400px', backgroundColor: '#fff', border: '1px solid #e2e8f0', borderRadius: '12px', padding: '28px' }}>
        <h1 style={{ fontSize: '22px', fontWeight: '800', color: '#0f172a', margin: '0 0 4px 0' }}>I-ITSM</h1>
        <p style={{ color: '#64748b', fontSize: '14px', margin: '0 0 20px 0' }}>
          {mode === 'login' ? 'Sign in to manage IT incidents.' : 'Create an employee account to report incidents.'}
        </p>

        {notice && (
          <div role="status" style={{ backgroundColor: '#dcfce7', color: '#166534', padding: '10px 12px', borderRadius: '6px', fontSize: '13px', marginBottom: '14px' }}>
            {notice}
          </div>
        )}
        {error && (
          <div role="alert" style={{ backgroundColor: '#fee2e2', color: '#991b1b', padding: '10px 12px', borderRadius: '6px', fontSize: '13px', marginBottom: '14px' }}>
            {error}
          </div>
        )}

        {mode === 'login' ? (
          <form onSubmit={handleLogin}>
            <label style={labelStyle}>
              User ID
              <input style={fieldStyle} value={id} onChange={(e) => setId(e.target.value)} autoComplete="username" required />
            </label>
            <label style={labelStyle}>
              Password
              <input style={fieldStyle} type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
            </label>
            <button type="submit" disabled={busy} style={primaryButton(busy)}>
              {busy ? 'Signing in…' : 'Sign in'}
            </button>
          </form>
        ) : (
          <form onSubmit={handleRegister}>
            <label style={labelStyle}>
              User ID
              <input style={fieldStyle} value={form.id} onChange={setField('id')} required />
            </label>
            <label style={labelStyle}>
              Full name
              <input style={fieldStyle} value={form.name} onChange={setField('name')} required />
            </label>
            <label style={labelStyle}>
              Email
              <input style={fieldStyle} type="email" value={form.email} onChange={setField('email')} required />
            </label>
            <label style={labelStyle}>
              Department
              <input style={fieldStyle} value={form.department} onChange={setField('department')} required />
            </label>
            <label style={labelStyle}>
              Password
              <input style={fieldStyle} type="password" value={form.password} onChange={setField('password')} autoComplete="new-password" required />
            </label>
            <button type="submit" disabled={busy} style={primaryButton(busy)}>
              {busy ? 'Creating account…' : 'Create account'}
            </button>
          </form>
        )}

        <button
          type="button"
          onClick={() => switchMode(mode === 'login' ? 'register' : 'login')}
          style={{ marginTop: '14px', background: 'transparent', border: 'none', color: '#2563eb', cursor: 'pointer', fontSize: '13px', fontWeight: '600', padding: 0 }}
        >
          {mode === 'login' ? 'New employee? Create an account' : 'Back to sign in'}
        </button>
      </div>
    </div>
  );
}

function primaryButton(busy) {
  return {
    width: '100%',
    padding: '10px 14px',
    borderRadius: '6px',
    border: 'none',
    backgroundColor: busy ? '#93c5fd' : '#2563eb',
    color: '#fff',
    fontSize: '14px',
    fontWeight: '600',
    cursor: busy ? 'default' : 'pointer',
  };
}
