import { useState, useEffect } from 'react';
import { adminApi } from '../../api/apiClient';
import { CATEGORY_ID_PATTERN } from './adminUtils';
import {
  card, heading, muted, table, th, td, label, input, grid, btnPrimary, btnSecondary, btnSmall, errorText, okText,
} from './adminStyles';

const EMPTY_FORM = { id: '', name: '', active: true, editingId: null };

/** List incident categories; create/rename them and activate/deactivate (GET/POST /api/admin/categories[/{id}/...]). */
export default function CategoriesPanel() {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [form, setForm] = useState(null); // null = hidden
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    async function fetchCategories() {
      try {
        const list = await adminApi.listCategories();
        if (!cancelled) setCategories(list);
      } catch (err) {
        if (!cancelled) setError(err.message || 'Failed to load categories');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    fetchCategories();
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  const setField = (name) => (e) => setForm((f) => ({ ...f, [name]: e.target.value }));

  function startCreate() {
    setError('');
    setMessage('');
    setForm({ ...EMPTY_FORM });
  }

  function startEdit(c) {
    setError('');
    setMessage('');
    // `active` is carried along so renaming never changes the category's status
    setForm({ id: c.id, name: c.name, active: c.active !== false, editingId: c.id });
  }

  async function submit(e) {
    e.preventDefault();
    setError('');
    setMessage('');
    const id = form.id.trim();
    const name = form.name.trim();
    if (!CATEGORY_ID_PATTERN.test(id)) {
      setError('Category ID must start with a capital letter and contain only capital letters, digits and underscores (e.g. NETWORK).');
      return;
    }
    if (!name) {
      setError('Category name is required.');
      return;
    }
    try {
      await adminApi.saveCategory({ id, name, active: form.active });
      setMessage(form.editingId ? `Updated ${id}.` : `Created ${id}.`);
      setForm(null);
      setReloadKey((k) => k + 1);
    } catch (err) {
      setError(err.message || 'Failed to save the category');
    }
  }

  async function toggleActive(c) {
    setError('');
    setMessage('');
    const next = c.active === false;
    try {
      await adminApi.setCategoryActive(c.id, next);
      setMessage(`${c.id} ${next ? 'activated' : 'deactivated'}.`);
      setReloadKey((k) => k + 1);
    } catch (err) {
      setError(err.message || 'The operation failed');
    }
  }

  const editing = form && form.editingId;

  return (
    <section style={card} aria-label="Categories">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={heading}>Categories</h2>
        <button type="button" style={btnPrimary} onClick={startCreate}>New category</button>
      </div>
      <p style={muted}>Incident categories. Inactive categories can no longer be chosen for new incidents.</p>

      {loading ? (
        <div style={muted}>Loading...</div>
      ) : categories.length === 0 ? (
        <div style={muted}>No categories defined.</div>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table style={table}>
            <thead>
              <tr>
                <th style={th}>ID</th>
                <th style={th}>Name</th>
                <th style={th}>Status</th>
                <th style={th}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {categories.map((c) => {
                const active = c.active !== false;
                return (
                  <tr key={c.id} data-testid="category-row">
                    <td style={td}><strong>{c.id}</strong></td>
                    <td style={td}>{c.name}</td>
                    <td style={td}>{active ? 'Active' : 'Inactive'}</td>
                    <td style={td}>
                      <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                        <button type="button" style={btnSmall} onClick={() => startEdit(c)}>Edit {c.id}</button>
                        <button type="button" style={btnSmall} onClick={() => toggleActive(c)}>
                          {active ? `Deactivate ${c.id}` : `Activate ${c.id}`}
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {form && (
        <form onSubmit={submit} aria-label="Category form" style={{ marginTop: '20px' }}>
          <h3 style={{ ...heading, fontSize: '14px' }}>{editing ? `Edit ${editing}` : 'Create category'}</h3>
          <div style={grid}>
            <div>
              <label htmlFor="cat-id" style={label}>Category ID</label>
              <input id="cat-id" value={form.id} onChange={setField('id')} style={input}
                placeholder="e.g. NETWORK" disabled={!!editing} />
            </div>
            <div>
              <label htmlFor="cat-name" style={label}>Name</label>
              <input id="cat-name" value={form.name} onChange={setField('name')} style={input} />
            </div>
          </div>
          <div style={{ display: 'flex', gap: '10px', marginTop: '14px' }}>
            <button type="submit" style={btnPrimary}>{editing ? 'Save changes' : 'Create category'}</button>
            <button type="button" style={btnSecondary} onClick={() => setForm(null)}>Cancel</button>
          </div>
        </form>
      )}

      {error && <div role="alert" style={errorText}>{error}</div>}
      {message && <div role="status" style={okText}>{message}</div>}
    </section>
  );
}
