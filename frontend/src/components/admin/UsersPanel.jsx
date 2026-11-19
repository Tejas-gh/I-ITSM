import { useState, useEffect } from 'react';
import { adminApi } from '../../api/apiClient';
import { ROLES } from '../../constants/itsmConstants';
import { parseSkills, formatSkills, isTechnicianRole } from './adminUtils';
import {
  card, heading, muted, table, th, td, label, input, grid, btnPrimary, btnSecondary, btnSmall, errorText, okText,
} from './adminStyles';

const EMPTY_FORM = {
  id: '', name: '', email: '', role: ROLES.EMPLOYEE, password: '',
  department: '', yearsOfExperience: '0', maxActiveIncidents: '5', skills: '',
};

/** List users and create/edit/activate them (GET/POST/PUT /api/admin/users, PUT /api/admin/technicians/{id}/availability). */
export default function UsersPanel() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [form, setForm] = useState(null); // null = hidden; { ...EMPTY_FORM, editingId? }

  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    async function fetchUsers() {
      try {
        const list = await adminApi.listUsers();
        if (!cancelled) setUsers(list);
      } catch (err) {
        if (!cancelled) setError(err.message || 'Failed to load users');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    fetchUsers();
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  const setField = (name) => (e) => setForm((f) => ({ ...f, [name]: e.target.value }));

  function startCreate() {
    setError('');
    setMessage('');
    setForm({ ...EMPTY_FORM, editingId: null });
  }

  function startEdit(u) {
    setError('');
    setMessage('');
    setForm({
      id: u.id, name: u.name, email: u.email, role: u.role, password: '',
      department: u.department || '',
      yearsOfExperience: String(u.yearsOfExperience ?? 0),
      maxActiveIncidents: String(u.maxActiveIncidents ?? 5),
      skills: formatSkills(u.skillLevels),
      editingId: u.id,
    });
  }

  async function run(action, successMessage) {
    setError('');
    setMessage('');
    try {
      await action();
      setMessage(successMessage);
      setReloadKey((k) => k + 1);
      return true;
    } catch (err) {
      setError(err.message || 'The operation failed');
      return false;
    }
  }

  async function submit(e) {
    e.preventDefault();
    let skillLevels;
    try {
      skillLevels = isTechnicianRole(form.role) ? parseSkills(form.skills) : undefined;
    } catch (err) {
      setError(err.message);
      return;
    }
    const detail = isTechnicianRole(form.role)
      ? {
          skillLevels,
          yearsOfExperience: Number(form.yearsOfExperience),
          maxActiveIncidents: Number(form.maxActiveIncidents),
        }
      : form.role === ROLES.EMPLOYEE
        ? { department: form.department }
        : {};

    const editing = form.editingId;
    const ok = await run(
      () => (editing
        ? adminApi.updateUser(editing, { name: form.name, email: form.email, ...detail })
        : adminApi.createUser({
            id: form.id, name: form.name, email: form.email, role: form.role, password: form.password, ...detail,
          })),
      editing ? `Updated ${editing}.` : `Created ${form.id}.`
    );
    if (ok) setForm(null);
  }

  const toggleActive = (u) =>
    run(() => adminApi.updateUser(u.id, { active: !u.active }), `${u.id} ${u.active ? 'deactivated' : 'activated'}.`);

  const toggleAvailability = (u) =>
    run(() => adminApi.setTechnicianAvailability(u.id, !u.available),
      `${u.id} marked ${u.available ? 'unavailable' : 'available'}.`);

  const editing = form && form.editingId;

  return (
    <section style={card} aria-label="Users">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={heading}>Users</h2>
        <button type="button" style={btnPrimary} onClick={startCreate}>New user</button>
      </div>
      <p style={muted}>Employees, technicians and administrators. Passwords are never displayed.</p>

      {loading ? (
        <div style={muted}>Loading...</div>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table style={table}>
            <thead>
              <tr>
                <th style={th}>ID</th>
                <th style={th}>Name</th>
                <th style={th}>Email</th>
                <th style={th}>Role</th>
                <th style={th}>Details</th>
                <th style={th}>Status</th>
                <th style={th}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id} data-testid="user-row">
                  <td style={td}><strong>{u.id}</strong></td>
                  <td style={td}>{u.name}</td>
                  <td style={td}>{u.email}</td>
                  <td style={td}>{u.role}</td>
                  <td style={td}>
                    {u.department && <span>{u.department}</span>}
                    {isTechnicianRole(u.role) && (
                      <span>
                        {formatSkills(u.skillLevels) || 'no skills'} · max {u.maxActiveIncidents} ·{' '}
                        {u.available ? 'available' : 'unavailable'}
                      </span>
                    )}
                  </td>
                  <td style={td}>{u.active ? 'Active' : 'Inactive'}</td>
                  <td style={td}>
                    <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                      <button type="button" style={btnSmall} onClick={() => startEdit(u)}>Edit {u.id}</button>
                      <button type="button" style={btnSmall} onClick={() => toggleActive(u)}>
                        {u.active ? `Deactivate ${u.id}` : `Activate ${u.id}`}
                      </button>
                      {isTechnicianRole(u.role) && (
                        <button type="button" style={btnSmall} onClick={() => toggleAvailability(u)}>
                          {u.available ? `Set ${u.id} unavailable` : `Set ${u.id} available`}
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {form && (
        <form onSubmit={submit} aria-label="User form" style={{ marginTop: '20px' }}>
          <h3 style={{ ...heading, fontSize: '14px' }}>{editing ? `Edit ${editing}` : 'Create user'}</h3>
          <div style={grid}>
            <div>
              <label htmlFor="u-id" style={label}>User ID</label>
              <input id="u-id" value={form.id} onChange={setField('id')} style={input} disabled={!!editing} />
            </div>
            <div>
              <label htmlFor="u-name" style={label}>Name</label>
              <input id="u-name" value={form.name} onChange={setField('name')} style={input} />
            </div>
            <div>
              <label htmlFor="u-email" style={label}>Email</label>
              <input id="u-email" value={form.email} onChange={setField('email')} style={input} />
            </div>
            <div>
              <label htmlFor="u-role" style={label}>Role</label>
              <select id="u-role" value={form.role} onChange={setField('role')} style={input} disabled={!!editing}>
                {Object.values(ROLES).map((r) => (
                  <option key={r} value={r}>{r}</option>
                ))}
              </select>
            </div>
            {!editing && (
              <div>
                <label htmlFor="u-password" style={label}>Password</label>
                <input id="u-password" type="password" value={form.password} onChange={setField('password')}
                  style={input} autoComplete="new-password" />
              </div>
            )}
            {form.role === ROLES.EMPLOYEE && (
              <div>
                <label htmlFor="u-dept" style={label}>Department</label>
                <input id="u-dept" value={form.department} onChange={setField('department')} style={input} />
              </div>
            )}
            {isTechnicianRole(form.role) && (
              <>
                <div>
                  <label htmlFor="u-years" style={label}>Years of experience</label>
                  <input id="u-years" type="number" min="0" value={form.yearsOfExperience}
                    onChange={setField('yearsOfExperience')} style={input} />
                </div>
                <div>
                  <label htmlFor="u-max" style={label}>Max active incidents</label>
                  <input id="u-max" type="number" min="1" value={form.maxActiveIncidents}
                    onChange={setField('maxActiveIncidents')} style={input} />
                </div>
                <div>
                  <label htmlFor="u-skills" style={label}>Skills (CATEGORY:level)</label>
                  <input id="u-skills" placeholder="NETWORK:3, DATABASE:2" value={form.skills}
                    onChange={setField('skills')} style={input} />
                </div>
              </>
            )}
          </div>
          <div style={{ display: 'flex', gap: '10px', marginTop: '14px' }}>
            <button type="submit" style={btnPrimary}>{editing ? 'Save changes' : 'Create user'}</button>
            <button type="button" style={btnSecondary} onClick={() => setForm(null)}>Cancel</button>
          </div>
        </form>
      )}

      {error && <div role="alert" style={errorText}>{error}</div>}
      {message && <div role="status" style={okText}>{message}</div>}
    </section>
  );
}
