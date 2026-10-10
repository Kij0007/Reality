import { api } from './api.js';
import {
  escapeHtml, formatDuration, formatDate, formatDateTime, todayISO,
  emptyState, errorState, loadingState, runAction, activityOptions,
  progressBar, badge,
} from './ui.js';

let selectedActivity = '';
let selectedDate = '';
export function reset() { selectedActivity = ''; selectedDate = ''; }

function sessionTable(sessions) {
  if (!sessions.length) {
    return emptyState('No completed sessions on this date',
      'Start a session from Activities or Sessions. Daily totals update after a session ends.',
      '<a class="button button-secondary" href="#sessions">Open sessions</a>');
  }

  return `<div class="table-wrap"><table>
    <thead><tr><th scope="col">Session</th><th scope="col">Started</th>
      <th scope="col">Ended</th><th scope="col">Session working time</th></tr></thead>
    <tbody>${sessions.map(session => `<tr>
      <td>#${escapeHtml(session.id)}</td>
      <td>${escapeHtml(formatDateTime(session.startTime))}</td>
      <td>${escapeHtml(formatDateTime(session.endTime))}</td>
      <td>${escapeHtml(formatDuration(session.duration ?? 0))}</td>
    </tr>`).join('')}</tbody>
  </table></div>
  <p class="muted">The day total counts the portion of each completed session on this date.
    Rows show the duration of the whole session, including sessions crossing midnight.</p>`;
}

function progressContent(progress, day, streak, activity) {
  const targetSeconds = Number(progress.minimumDuration) * 60;
  const isScheduled = activity.scheduledDays?.includes(
    new Intl.DateTimeFormat('en-US', { weekday: 'long' })
      .format(new Date(`${progress.date}T12:00:00`)).toUpperCase());
  const beforeStart = progress.date < activity.startDate;
  const scheduleNote = beforeStart
    ? 'This date is before the commitment start date and is excluded from streaks and monthly reports.'
    : isScheduled
      ? 'This is a scheduled commitment day.'
      : 'This is an unscheduled day. Work counts toward recorded time, but this day does not affect the streak.';

  return `<div class="stat-grid">
    <article class="stat-card"><span class="eyebrow">Recorded day time</span>
      <strong>${escapeHtml(formatDuration(progress.totalDuration ?? 0))}</strong>
      <span class="muted">${escapeHtml(formatDate(progress.date))}</span></article>
    <article class="stat-card"><span class="eyebrow">Daily target</span>
      <strong>${escapeHtml(formatDuration(targetSeconds))}</strong>
      <span class="muted">${escapeHtml(progress.minimumDuration)} minutes</span></article>
    <article class="stat-card"><span class="eyebrow">Target status</span>
      <strong>${progress.completed ? 'Completed' : 'Pending'}</strong>
      <span class="muted">${progress.completed ? 'The daily target has been reached.' : 'More recorded time is needed.'}</span></article>
    <article class="stat-card"><span class="eyebrow">Current streak</span>
      <strong>${escapeHtml(streak.currentStreak ?? 0)}</strong>
      <span class="muted">Completed scheduled days</span></article>
  </div>
  <section class="panel">
    <div class="toolbar"><div><h2>${escapeHtml(progress.activityName)}</h2>
      <p class="muted">${escapeHtml(formatDate(progress.date))} · Activity #${escapeHtml(progress.activityId)}</p></div>
      ${badge(progress.completed ? 'Target reached' : 'Target pending', progress.completed ? 'success' : 'warning')}
    </div>
    ${progressBar(progress.totalDuration ?? 0, targetSeconds)}
    <p class="muted">${escapeHtml(scheduleNote)}</p>
    <p class="muted">The current streak is evaluated through ${escapeHtml(formatDate(streak.lastEvaluatedDate))},
      regardless of the date selected above. Scheduled days count; unscheduled days are skipped.</p>
  </section>
  <section class="panel"><h2>Sessions on this date</h2>
    <p class="muted">${escapeHtml(day.sessions?.length ?? 0)} completed sessions ·
      ${escapeHtml(formatDuration(day.totalDuration ?? 0))} recorded on this date.</p>
    ${sessionTable(day.sessions ?? [])}
  </section>`;
}

export async function render(ctx) {
  const activities = ctx.store.activities.filter(activity => activity.active !== false);
  if (!activities.length) {
    ctx.root.innerHTML = emptyState('Build your first commitment',
      'Create an activity to see daily progress and scheduled-day streaks.',
      '<a class="button button-primary" href="#activities">Create an activity</a>');
    return;
  }

  if (!activities.some(activity => String(activity.id) === selectedActivity)) {
    selectedActivity = String(activities[0].id);
  }
  selectedDate ||= todayISO();
  ctx.root.innerHTML = `<section class="panel">
    <h2>Daily progress</h2>
    <p class="muted">Review recorded sessions, daily targets, and your current streak.</p>
    <form data-progress-form class="form-grid">
      <label class="field">Activity<select name="activityId" required>
        ${activityOptions(activities, selectedActivity)}</select></label>
      <label class="field">Date<input type="date" name="date" value="${escapeHtml(selectedDate)}" required></label>
      <div class="field"><span aria-hidden="true">&nbsp;</span>
        <button class="button button-primary" type="submit">View progress</button></div>
    </form>
  </section>
  <div data-progress-result aria-live="polite"></div>`;

  const result = ctx.root.querySelector('[data-progress-result]');
  let requestNumber = 0;

  async function loadProgress() {
    const thisRequest = ++requestNumber;
    result.innerHTML = loadingState('Loading daily progress…');
    result.setAttribute('aria-busy', 'true');
    try {
      const [progress, day, streak] = await Promise.all([
        api.progress.day(selectedActivity, selectedDate, { signal: ctx.signal }),
        api.sessions.day(selectedActivity, selectedDate, { signal: ctx.signal }),
        api.streaks.get(selectedActivity, { signal: ctx.signal }),
      ]);
      if (ctx.signal.aborted || thisRequest !== requestNumber) return;
      const activity = activities.find(item => String(item.id) === selectedActivity);
      result.innerHTML = progressContent(progress, day, streak, activity);
    } catch (error) {
      if (ctx.signal.aborted || error.name === 'AbortError' || thisRequest !== requestNumber) return;
      result.innerHTML = errorState(error.message || 'Daily progress could not be loaded. Please try again.');
    } finally {
      if (!ctx.signal.aborted && thisRequest === requestNumber) result.removeAttribute('aria-busy');
    }
  }

  ctx.root.addEventListener('submit', async event => {
    const form = event.target.closest('[data-progress-form]');
    if (!form) return;
    event.preventDefault();
    if (!form.reportValidity()) return;
    const values = new FormData(form);
    selectedActivity = String(values.get('activityId'));
    selectedDate = String(values.get('date'));
    await runAction(form.querySelector('button[type="submit"]'), loadProgress, 'Loading…');
  }, { signal: ctx.signal });

  await loadProgress();
}
