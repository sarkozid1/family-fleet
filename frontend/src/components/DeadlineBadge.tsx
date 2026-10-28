import type { Deadline } from '../api/types';
import { daysLabel, deadlineStatusLabels, urgency } from '../labels';

/** Nyitott határidőnél a hátralévő idő (színezve), lezártnál az állapot. */
export function DeadlineBadge({ deadline }: { deadline: Deadline }) {
  if (deadline.status !== 'OPEN') {
    return <span className="badge neutral">{deadlineStatusLabels[deadline.status]}</span>;
  }
  return <span className={`badge ${urgency(deadline.daysUntilDue)}`}>{daysLabel(deadline.daysUntilDue)}</span>;
}
