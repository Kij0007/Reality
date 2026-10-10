export const CATEGORIES = ['STUDY', 'FITNESS', 'WORK', 'LEARNING', 'HEALTH', 'PERSONAL', 'OTHER'];
export const WEEKDAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
export const label = value => value ? value.charAt(0) + value.slice(1).toLowerCase() : 'Uncategorized';
export const activityName = (store, id) => store.activities.find(activity => activity.id === id)?.name || `Activity #${id} (deleted or unavailable)`;

export function isScheduled(activity, date) {
  const weekday = WEEKDAYS[(new Date(`${date}T12:00:00`).getDay() + 6) % 7];
  return activity.active && activity.startDate && date >= activity.startDate && (activity.scheduledDays || []).includes(weekday);
}

export function sessionState(session, breaks) {
  if (session.endTime) return 'Completed';
  if (!breaks) return 'Checking state';
  return breaks.some(item => !item.endTime) ? 'On break' : 'Running';
}
