import { api } from './api.js';
import { isScheduled, label } from './domain.js';
import { runningCards, updateTimers } from './sessions.js';
import { escapeHtml as e, todayISO, formatDate, formatDuration, emptyState, loadingState, errorState, progressBar, badge } from './ui.js';

function missingScheduleFields(activity) {
  const missing = [];
  if (!activity.startDate) missing.push('commitment start date');
  if (!Array.isArray(activity.scheduledDays) || !activity.scheduledDays.length) missing.push('repeat days');
  return missing;
}

async function loadActivityProgress(activity, today, signal) {
  const missingFields = missingScheduleFields(activity);
  // Daily work remains available even when a legacy record cannot have a streak calculated.
  const [progressResult, streakResult] = await Promise.allSettled([
    api.progress.day(activity.id, today, { signal }),
    missingFields.length ? Promise.resolve(null) : api.streaks.get(activity.id, { signal })
  ]);
  const progress = progressResult.status === 'fulfilled' ? progressResult.value : null;
  const validProgress = progress && Number.isFinite(progress.totalDuration) && typeof progress.completed === 'boolean';
  return {
    activity,
    missingFields,
    progress: validProgress ? progress : null,
    progressError: progressResult.status === 'rejected'
      ? progressResult.reason.message || 'Daily progress could not be loaded.'
      : validProgress ? '' : 'The server returned an unexpected daily-progress response.',
    streak: streakResult.status === 'fulfilled' ? streakResult.value : null,
    streakError: streakResult.status === 'rejected'
      ? streakResult.reason.message || 'The current streak could not be loaded.'
      : missingFields.length ? `Add ${missingFields.join(' and ')} to calculate a streak.` : ''
  };
}

function scheduleNotice(items) {
  const incomplete = items.filter(item => item.missingFields.length);
  if (!incomplete.length) return '';
  return `<section class="panel" aria-labelledby="schedule-attention-title"><h3 id="schedule-attention-title">Complete activity schedules</h3><p class="muted">These activities are missing schedule information. Their recorded work can still be shown, but a current streak needs a complete schedule. Choose the date your commitment actually began.</p><div class="schedule-list">${incomplete.map(({ activity, missingFields }) => `<article class="schedule-row"><div><h4>${e(activity.name || `Activity #${activity.id}`)}</h4><p class="muted small">Missing ${e(missingFields.join(' and '))}.</p></div><button class="button button-secondary button-small" data-action="edit-activity" data-id="${e(activity.id)}">Edit schedule</button></article>`).join('')}</div></section>`;
}

function requestErrors(items) {
  const failures = items.filter(item => item.progressError || (!item.missingFields.length && item.streakError));
  if (!failures.length) return '';
  return `<section class="panel" role="alert"><h3>Some data could not be loaded</h3><p class="muted">Use Refresh data to retry. Available daily progress is still shown.</p>${failures.map(item => errorState(`${item.activity.name || `Activity #${item.activity.id}`}: ${[item.progressError && `Daily progress: ${item.progressError}`, !item.missingFields.length && item.streakError && `Current streak: ${item.streakError}`].filter(Boolean).join(' ')}`)).join('')}</section>`;
}

export async function render(ctx) {
  const today = todayISO();
  const scheduled = ctx.store.activities.filter(activity => isScheduled(activity, today));
  ctx.root.innerHTML = `<div class="section-heading"><div><p class="eyebrow">${e(formatDate(today))}</p><h2>Show up for yourself.</h2><p class="muted">A clear view of today’s commitments and the work you’ve recorded.</p></div><button class="button button-primary" data-action="create-activity">+ Create activity</button></div><div id="dashboard-stats" class="stat-grid">${loadingState('Loading today’s progress…')}</div><div class="dashboard-columns"><section class="panel"><div class="panel-heading"><div><h3>Today’s commitments</h3><p class="muted small">${scheduled.length} scheduled ${scheduled.length === 1 ? 'activity' : 'activities'}</p></div><a class="text-link" href="#schedule">View schedule →</a></div><div id="today-list">${loadingState()}</div></section><section class="panel intention-panel"><span class="eyebrow">Keep your rhythm</span><h3>Consistency begins with one session.</h3><p class="muted">Choose a commitment, give it your attention, and let Reality keep the record.</p><a class="button button-secondary" href="#activities">Browse activities</a><div class="intention-divider"></div><h4>How your target works</h4><p class="muted small">Finish a session to record your work. Breaks are excluded. Streaks count completed scheduled days through yesterday.</p><a class="text-link" href="#progress">Explore your progress →</a></section></div><section><div class="subheading"><h3>Sessions in progress</h3><a class="text-link" href="#sessions">All sessions →</a></div>${runningCards(ctx)}</section><p class="page-note">Daily totals use finished sessions. Live timers are estimates; dates and streak evaluation use the server’s local time.</p>`;
  updateTimers(ctx);
  const results = await Promise.all(ctx.store.activities.map(activity => loadActivityProgress(activity, today, ctx.signal)));
  if (ctx.signal.aborted) return;
  const data = new Map(results.map(item => [item.activity.id, item]));
  const progressFailures = results.filter(item => item.progressError);
  const completed = scheduled.filter(activity => data.get(activity.id)?.progress?.completed).length;
  const work = progressFailures.length ? null : results.reduce((sum, item) => sum + item.progress.totalDuration, 0);
  const stats = [
    ['Active activities', ctx.store.activities.length, 'Your current commitments'],
    ['Scheduled today', scheduled.length, 'Based on your repeat days'],
    ['Targets completed', scheduled.some(activity => data.get(activity.id)?.progressError) ? '—' : `${completed} / ${scheduled.length}`, 'From finished sessions today'],
    ['Recorded work today', work === null ? '—' : formatDuration(work), 'Across active activities']
  ];
  ctx.root.querySelector('#dashboard-stats').innerHTML = stats.map(([title, value, description]) => `<article class="stat-card"><span class="eyebrow">${e(title)}</span><strong>${e(value)}</strong><span class="muted small">${e(description)}</span></article>`).join('');
  ctx.root.querySelector('#today-list').innerHTML = !ctx.store.activities.length ? emptyState('Build your first commitment', 'Create an activity to get started.', '<button class="button button-primary" data-action="create-activity">Create activity</button>') : !scheduled.length ? emptyState('A little room to breathe', 'Nothing is scheduled for today. You can still start any active activity.', '<a class="button button-secondary" href="#activities">Browse activities</a>') : `<div class="today-list">${scheduled.map(activity => {
    const item = data.get(activity.id);
    const running = ctx.store.sessions.some(session => session.activityId === activity.id && !session.endTime);
    return `<article class="today-row"><div class="today-row-heading"><div><h4>${e(activity.name || `Activity #${activity.id}`)}</h4><span class="muted small">${e(label(activity.category))} · ${activity.minimumDuration} min target</span></div>${item.progress ? badge(item.progress.completed ? 'Target met' : running ? 'In progress' : 'Pending', item.progress.completed ? 'success' : 'neutral') : badge('Unavailable', 'warning')}</div>${item.progress ? `${progressBar(item.progress.totalDuration, activity.minimumDuration * 60)}<div class="progress-caption"><span>${e(formatDuration(item.progress.totalDuration))} recorded</span><span>${item.streak ? `${e(item.streak.currentStreak)} day streak` : 'Current streak unavailable'}</span></div>` : errorState(item.progressError)}<div class="row-actions"><button class="button button-secondary button-small" data-action="start-session" data-id="${activity.id}" ${running ? 'disabled' : ''}>${running ? 'Session running' : 'Start session'}</button><button class="button button-quiet button-small" data-action="view-activity" data-id="${activity.id}">Details</button></div></article>`;
  }).join('')}</div>`;
  ctx.root.querySelector('#dashboard-stats').insertAdjacentHTML('afterend', scheduleNotice(results) + requestErrors(results));
}
