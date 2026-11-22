const OFFSET_STORAGE_KEY = 'iitsm_simulated_time_offset_ms';

export function getSimulatedTimeOffset() {
  try {
    return Number(localStorage.getItem(OFFSET_STORAGE_KEY) || 0);
  } catch {
    return 0;
  }
}

export function getSimulatedNow() {
  return new Date(Date.now() + getSimulatedTimeOffset());
}

export function advanceSimulatedTime(ms = 270000) {
  const current = getSimulatedTimeOffset();
  const next = current + ms;
  try {
    localStorage.setItem(OFFSET_STORAGE_KEY, String(next));
  } catch {
    // ignore
  }
  window.dispatchEvent(new CustomEvent('iitsm_clock_advanced', { detail: { offset: next, addedMs: ms } }));
  return next;
}

export function resetSimulatedTime() {
  try {
    localStorage.removeItem(OFFSET_STORAGE_KEY);
  } catch {
    // ignore
  }
  window.dispatchEvent(new CustomEvent('iitsm_clock_advanced', { detail: { offset: 0, addedMs: 0 } }));
  return 0;
}
