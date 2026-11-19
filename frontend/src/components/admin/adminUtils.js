/** "NETWORK:3, DATABASE:2" -> { NETWORK: 3, DATABASE: 2 }. Throws Error with a readable message on bad input. */
export function parseSkills(text) {
  const skills = {};
  const trimmed = (text || '').trim();
  if (trimmed === '') return skills;
  trimmed.split(',').forEach((part) => {
    const [rawId, rawLevel, ...rest] = part.split(':').map((s) => s.trim());
    const level = Number(rawLevel);
    if (!rawId || rest.length > 0 || !Number.isInteger(level) || level < 1 || level > 5) {
      throw new Error(`Invalid skill "${part.trim()}": use CATEGORY:level with level 1-5`);
    }
    skills[rawId.toUpperCase()] = level;
  });
  return skills;
}

/** { NETWORK: 3 } -> "NETWORK:3" */
export function formatSkills(skills) {
  return Object.entries(skills || {}).map(([id, level]) => `${id}:${level}`).join(', ');
}

export const isTechnicianRole = (role) => role === 'TECHNICIAN' || role === 'TEAM_LEAD';
