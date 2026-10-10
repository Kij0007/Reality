import { api } from './api.js';
import {
  escapeHtml, formatDuration, todayISO, emptyState, errorState,
  loadingState, runAction, activityOptions, progressBar,
} from './ui.js';

let selectedActivity = '';
let selectedMonth = '';
export function reset() { selectedActivity = ''; selectedMonth = ''; }

function metric(label, value, detail) {
  return `<article class="stat-card"><span class="eyebrow">${escapeHtml(label)}</span>
    <strong>${escapeHtml(value)}</strong><span class="muted">${escapeHtml(detail)}</span></article>`;
}

function reportContent(report) {
  const percentage = Number(report.completionPercentage ?? 0);
  const monthLabel = `${report.year}-${String(report.month).padStart(2, '0')}`;
  return `<section class="panel">
    <div class="toolbar"><div><h2>${escapeHtml(report.activityName)}</h2>
      <p class="muted">${escapeHtml(monthLabel)} · Activity #${escapeHtml(report.activityId)}</p></div>
      <span class="status">Monthly report</span></div>
    <p class="muted">Completion, missed days, and the monthly best streak use finished scheduled days.
      Today is included in recorded time after sessions end.</p>
  </section>
  <div class="stat-grid">
    ${metric('Recorded time', formatDuration(report.totalDuration ?? 0), 'Includes work on unscheduled days')}
    ${metric('Sessions', report.totalSessions ?? 0, 'Completed sessions overlapping this period')}
    ${metric('Scheduled days', report.scheduledDays ?? 0, 'Finished commitment days')}
    ${metric('Completed days', report.completedDays ?? 0, 'Daily target reached')}
    ${metric('Missed days', report.missedDays ?? 0, 'Scheduled days below the daily target')}
    ${metric('Completion', `${percentage.toFixed(2)}%`, 'Completed ÷ scheduled days')}
    ${metric('Best streak this month', report.longestStreak ?? 0, 'Consecutive completed scheduled days')}
    ${metric('Current streak', report.currentStreak ?? 0, 'Current through yesterday, across months')}
  </div>
  <section class="panel"><h2>Monthly completion</h2>
    ${progressBar(report.completedDays ?? 0, report.scheduledDays ?? 0, 'Monthly scheduled-day completion')}
    <p class="muted">${escapeHtml(report.completedDays ?? 0)} of ${escapeHtml(report.scheduledDays ?? 0)}
      finished scheduled days completed.</p>
    <div class="table-wrap"><table>
      <thead><tr><th scope="col">Result</th><th scope="col">Days</th></tr></thead>
      <tbody><tr><td>Target reached</td><td>${escapeHtml(report.completedDays ?? 0)}</td></tr>
        <tr><td>Target missed</td><td>${escapeHtml(report.missedDays ?? 0)}</td></tr>
        <tr><td>Total evaluated scheduled days</td><td>${escapeHtml(report.scheduledDays ?? 0)}</td></tr></tbody>
    </table></div>
    ${Number(report.scheduledDays ?? 0) === 0
      ? '<p class="muted">No finished scheduled days fall in this report period. The report starts at the commitment start date and excludes future days.</p>'
      : ''}
    <p class="muted">Unscheduled days neither extend nor reset a streak. The current streak uses
      server yesterday and is independent of the selected month. Editing the commitment start date,
      schedule, or target also changes how historical progress is evaluated.</p>
  </section>`;
}

export async function render(ctx) {
  const activities = ctx.store.activities.filter(activity => activity.active !== false);
  if (!activities.length) {
    ctx.root.innerHTML = emptyState('No reports yet',
      'Create an activity and record sessions to build your monthly report.',
      '<a class="button button-primary" href="#activities">Create an activity</a>');
    return;
  }

  if (!activities.some(activity => String(activity.id) === selectedActivity)) {
    selectedActivity = String(activities[0].id);
  }
  selectedMonth ||= todayISO().slice(0, 7);
  ctx.root.innerHTML = `<section class="panel">
    <h2>Monthly reports</h2>
    <p class="muted">Review monthly time, completion, and streaks for a commitment.</p>
    <form data-report-form class="form-grid">
      <label class="field">Activity<select name="activityId" required>
        ${activityOptions(activities, selectedActivity)}</select></label>
      <label class="field">Month<input type="month" name="month" value="${escapeHtml(selectedMonth)}"
        pattern="[0-9]{4}-[0-9]{2}" required></label>
      <div class="field"><span aria-hidden="true">&nbsp;</span>
        <button class="button button-primary" type="submit">View report</button></div>
    </form>
  </section>
  <div data-report-result aria-live="polite"></div>`;

  const result = ctx.root.querySelector('[data-report-result]');
  let requestNumber = 0;

  async function loadReport() {
    const thisRequest = ++requestNumber;
    result.innerHTML = loadingState('Loading monthly report…');
    result.setAttribute('aria-busy', 'true');
    try {
      const report = await api.reports.month(selectedActivity, selectedMonth, { signal: ctx.signal });
      if (ctx.signal.aborted || thisRequest !== requestNumber) return;
      result.innerHTML = reportContent(report);
    } catch (error) {
      if (ctx.signal.aborted || error.name === 'AbortError' || thisRequest !== requestNumber) return;
      result.innerHTML = errorState(error.message || 'The monthly report could not be loaded. Please try again.');
    } finally {
      if (!ctx.signal.aborted && thisRequest === requestNumber) result.removeAttribute('aria-busy');
    }
  }

  ctx.root.addEventListener('submit', async event => {
    const form = event.target.closest('[data-report-form]');
    if (!form) return;
    event.preventDefault();
    if (!form.reportValidity()) return;
    const values = new FormData(form);
    selectedActivity = String(values.get('activityId'));
    selectedMonth = String(values.get('month'));
    await runAction(form.querySelector('button[type="submit"]'), loadReport, 'Loading…');
  }, { signal: ctx.signal });

  await loadReport();
}

