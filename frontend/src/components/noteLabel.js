/** Notes recorded by the backend when a resolution is rejected start with this marker. */
export const REJECTION_MARKER = 'RESOLUTION_REJECTED';

/** Label shown on an activity-history note: the rejection marker wins over the generic note type. */
export function noteLabel(note) {
  if (note?.text?.startsWith(REJECTION_MARKER)) return 'RESOLUTION REJECTED';
  return (note?.type || '').replace('_', ' ');
}
