/** Notes recorded by the backend when a resolution is rejected start with this marker. */
export const REJECTION_MARKER = 'RESOLUTION_REJECTED';

/** The employee's optional remark is saved as an Additional Info note starting with this prefix. */
export const REJECTION_REMARK_PREFIX = 'Rejection remark: ';

/** Label shown on an activity-history note: the rejection marker wins over the generic note type. */
export function noteLabel(note) {
  if (note?.text?.startsWith(REJECTION_MARKER)) return 'RESOLUTION REJECTED';
  return (note?.type || '').replace('_', ' ');
}

/**
 * Shows the remark inside the rejection entry: a rejection note directly followed by the same author's
 * "Rejection remark: ..." note becomes one entry, so the activity log reads "RESOLUTION REJECTED ... Remark: ...".
 */
export function mergeRejectionRemarks(notes) {
  const result = [];
  for (let i = 0; i < notes.length; i += 1) {
    const note = notes[i];
    const next = notes[i + 1];
    if (
      note.text?.startsWith(REJECTION_MARKER)
      && next?.authorId === note.authorId
      && next.text?.startsWith(REJECTION_REMARK_PREFIX)
    ) {
      result.push({ ...note, text: `${note.text}\n\nRemark: ${next.text.slice(REJECTION_REMARK_PREFIX.length)}` });
      i += 1;
    } else {
      result.push(note);
    }
  }
  return result;
}
