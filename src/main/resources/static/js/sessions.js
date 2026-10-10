import { api } from './api.js';
import { activityName, sessionState } from './domain.js';
import { escapeHtml as e, formatDateTime, formatDuration, activityOptions, emptyState, loadingState, errorState, badge } from './ui.js';

let selectedActivity = '';
let selectedDate = '';
let selectedStatus = '';
export function reset() { selectedActivity = ''; selectedDate = ''; selectedStatus = ''; }

export function sessionControls(session, breaks) {
  if (session.endTime) return '';
  const paused = breaks?.some(item => !item.endTime);
  return `${breaks ? `<button class="button button-secondary button-small" data-action="${paused ? 'resume-session' : 'break-session'}" data-id="${session.id}">${paused ? 'Resume' : 'Take break'}</button>` : ''}<button class="button button-primary button-small" data-action="stop-session" data-id="${session.id}">Finish session</button>`;
}

export function runningCards(ctx) {
  const sessions = ctx.store.sessions.filter(session => !session.endTime);
  if (!sessions.length) return emptyState('Ready when you are', 'Start a session from an activity to record your work.', '<a class="button button-secondary" href="#activities">Choose an activity</a>');
  return `<div class="session-grid">${sessions.map(session => {
    const breaks = ctx.store.breaks.get(session.id);
    const state = sessionState(session, breaks);
    const paused = state === 'On break';
    return `<article class="running-card ${paused ? 'is-paused' : ''}"><div class="card-top">${badge(state, paused ? 'warning' : breaks ? 'success' : 'neutral')}<span class="muted small">#${session.id}</span></div><h3>${e(activityName(ctx.store, session.activityId))}</h3><div class="session-timer" data-timer-id="${session.id}" aria-label="Estimated work time">${breaks ? '00:00:00' : '—'}</div><p class="muted small">${paused ? 'Work timer paused' : 'Estimated work time'} · Started ${e(formatDateTime(session.startTime))}</p>${ctx.store.breakErrors.get(session.id) ? errorState('Break state could not be loaded. Refresh before taking a break or resuming.') : ''}<div class="card-actions">${sessionControls(session, breaks)}<button class="button button-quiet button-small" data-action="view-session" data-id="${session.id}">Details</button></div></article>`;
  }).join('')}</div>`;
}

export function updateTimers(ctx) {
  const now = Date.now();
  ctx.root.querySelectorAll('[data-timer-id]').forEach(element => {
    const session = ctx.store.sessions.find(item => item.id === Number(element.dataset.timerId));
    const breaks = session && ctx.store.breaks.get(session.id);
    if (!session || !breaks || session.endTime) return;
    const activeBreak = breaks.find(item => !item.endTime);
    const timerEnd = activeBreak ? new Date(activeBreak.startTime).getTime() : now;
    const elapsed = Math.floor((timerEnd - new Date(session.startTime).getTime()) / 1000);
    const breakSeconds = breaks.reduce((sum, item) => sum + (item.endTime ? Number(item.duration || 0) : 0), 0);
    const total = Math.max(0, elapsed - breakSeconds);
    if (!Number.isFinite(total)) { element.textContent = '—'; return; }
    element.textContent = [Math.floor(total / 3600), Math.floor((total % 3600) / 60), total % 60].map(value => String(value).padStart(2, '0')).join(':');
  });
}

function historyTable(ctx, sessions) {
  const rows = sessions.filter(session => !selectedStatus || (selectedStatus === 'completed' ? !!session.endTime : !session.endTime)).slice().sort((a, b) => String(b.startTime).localeCompare(String(a.startTime)));
  return rows.length ? `<div class="table-wrap"><table><thead><tr><th>Activity / session</th><th>Started</th><th>Finished</th><th>Work time</th><th>State</th><th class="text-right">Actions</th></tr></thead><tbody>${rows.map(session => {
    const state = sessionState(session, ctx.store.breaks.get(session.id));
    return `<tr><td><strong>${e(activityName(ctx.store, session.activityId))}</strong><span class="muted small block">Session #${session.id}</span></td><td>${e(formatDateTime(session.startTime))}</td><td>${e(formatDateTime(session.endTime))}</td><td>${e(formatDuration(session.duration))}</td><td>${badge(state, state === 'On break' ? 'warning' : session.endTime ? 'neutral' : 'success')}</td><td><div class="table-actions"><button class="button button-secondary button-small" data-action="view-session" data-id="${session.id}">Details</button><button class="button button-quiet button-small danger-text" data-action="delete-session" data-id="${session.id}">Delete</button></div></td></tr>`;
  }).join('')}</tbody></table></div>` : emptyState('No sessions match these filters', 'Change the filters or start a session from an activity.');
}

export async function render(ctx) {
  if (!ctx.store.activities.some(activity => String(activity.id) === selectedActivity)) selectedActivity = '';
  ctx.root.innerHTML = `<div class="section-heading"><div><p class="eyebrow">Make the time count</p><h2>Your sessions</h2><p class="muted">Start, take a break, resume, and finish. Every session is saved.</p></div><a class="button button-primary" href="#activities">Start from an activity</a></div><section><div class="subheading"><h3>In progress</h3><span class="muted small">Live timers are estimates</span></div>${runningCards(ctx)}</section><section class="panel history-panel"><div class="panel-heading"><div><h3>Session history</h3><p class="muted small">Recorded work excludes breaks. Date totals include only finished sessions.</p></div></div><form id="history-filter" class="toolbar"><label class="field">Activity<select name="activity">${activityOptions(ctx.store.activities, selectedActivity, true)}</select></label><label class="field">Date <span class="muted">optional</span><input type="date" name="date" value="${e(selectedDate)}"></label><label class="field">State<select name="status"><option value="">All states</option><option value="completed" ${selectedStatus === 'completed' ? 'selected' : ''}>Completed</option><option value="running" ${selectedStatus === 'running' ? 'selected' : ''}>In progress</option></select></label><button class="button button-secondary" type="submit">Apply filters</button><button class="button button-quiet" type="button" id="clear-history">Clear</button></form><div id="history-results" aria-live="polite"></div></section><p class="page-note">Session timestamps use the server’s local time. Open sessions enter daily progress and reports when finished.</p>`;
  let requestNumber = 0;
  const loadHistory = async () => {
    const currentRequest = ++requestNumber;
    const results = ctx.root.querySelector('#history-results');
    if (selectedDate && !selectedActivity) { results.innerHTML = errorState('Choose an activity to view sessions for a specific date.'); return; }
    results.innerHTML = loadingState('Loading session history…');
    results.setAttribute('aria-busy', 'true');
    try {
      let sessions;
      let summary = '';
      if (selectedDate) {
        const day = await api.sessions.day(selectedActivity, selectedDate, { signal: ctx.signal });
        sessions = day.sessions;
        summary = `<div class="history-summary"><strong>${e(formatDuration(day.totalDuration))}</strong> recorded on this date. Individual rows show whole-session work time.</div>`;
      } else if (selectedActivity) sessions = await api.sessions.byActivity(selectedActivity, { signal: ctx.signal });
      else sessions = ctx.store.sessions;
      if (!ctx.signal.aborted && currentRequest === requestNumber) results.innerHTML = summary + historyTable(ctx, sessions);
    } catch (error) { if (!ctx.signal.aborted && currentRequest === requestNumber) results.innerHTML = errorState(error.message); }
    finally { if (currentRequest === requestNumber) results.removeAttribute('aria-busy'); }
  };
  ctx.root.querySelector('#history-filter').addEventListener('submit', event => {
    event.preventDefault();
    const data = new FormData(event.target);
    selectedActivity = String(data.get('activity'));
    selectedDate = String(data.get('date'));
    selectedStatus = String(data.get('status'));
    loadHistory();
  }, { signal: ctx.signal });
  ctx.root.querySelector('#clear-history').addEventListener('click', () => {
    selectedActivity = ''; selectedDate = ''; selectedStatus = '';
    const form = ctx.root.querySelector('#history-filter');
    form.querySelectorAll('input,select').forEach(input => { input.value = ''; });
    loadHistory();
  }, { signal: ctx.signal });
  updateTimers(ctx);
  await loadHistory();
}
