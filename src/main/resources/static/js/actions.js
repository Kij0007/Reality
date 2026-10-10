import { api } from './api.js';
import { CATEGORIES, WEEKDAYS, label, activityName, sessionState } from './domain.js';
import { escapeHtml as e, formatDate, formatDateTime, formatDuration, todayISO, badge, notify, runAction, openDialog } from './ui.js';

let mutationPending = false;
async function mutate(task) {
  if (mutationPending) throw new Error('Another change is being saved. Please wait a moment.');
  mutationPending = true;
  try { return await task(); }
  finally { mutationPending = false; }
}

async function changed(ctx, message) {
  if (ctx.signal.aborted) return;
  notify(message);
  // The write has succeeded. A failed refresh must not invite a duplicate save.
  await ctx.refresh();
}

function requireCurrentScreen(ctx) {
  if (ctx.signal.aborted) throw new DOMException('Request cancelled', 'AbortError');
}

function activityForm(activity = {}) {
  const days = activity.scheduledDays || WEEKDAYS;
  return `<div class="form-grid"><label class="field field-full">Activity name<input name="name" autocomplete="off" required maxlength="255" value="${e(activity.name)}" placeholder="What will you commit to?"></label><label class="field">Daily target <span class="muted">minutes</span><input name="minimumDuration" type="number" min="1" max="2147483647" step="1" required value="${e(activity.minimumDuration ?? 30)}"></label><label class="field">Category<select name="category" required><option value="">Choose a category</option>${CATEGORIES.map(category => `<option value="${category}" ${category === activity.category ? 'selected' : ''}>${label(category)}</option>`).join('')}</select></label><label class="field field-full">Commitment start date<input name="startDate" type="date" required value="${e(activity.startDate || todayISO())}"></label></div><fieldset class="day-fieldset"><legend>Repeat on <span class="muted">Choose at least one day</span></legend><div class="day-picker">${WEEKDAYS.map(day => `<label class="day-option"><input name="scheduledDays" type="checkbox" value="${day}" ${days.includes(day) ? 'checked' : ''}><span>${day.slice(0, 3)}</span></label>`).join('')}</div></fieldset>${activity.id ? '<p class="form-note">Changing the target, start date, or repeat days also changes how existing progress and reports are evaluated.</p>' : '<p class="form-note">Your daily target is met by recorded work time. Break time is excluded.</p>'}`;
}

async function editActivity(ctx, activityId) {
  const activity = activityId ? await api.activities.get(activityId, { signal: ctx.signal }) : {};
  requireCurrentScreen(ctx);
  openDialog({
    title: activityId ? 'Edit activity' : 'Create an activity', body: activityForm(activity), submitLabel: activityId ? 'Save changes' : 'Create activity',
    onSubmit: async data => {
      const name = String(data.get('name') || '').trim();
      const minimumDuration = Number(data.get('minimumDuration'));
      const scheduledDays = data.getAll('scheduledDays');
      if (!name) throw new Error('Enter an activity name.');
      if (!Number.isInteger(minimumDuration) || minimumDuration < 1 || minimumDuration > 2147483647) throw new Error('Enter a positive whole number of minutes.');
      if (!scheduledDays.length) throw new Error('Choose at least one repeat day.');
      const body = { name, minimumDuration, category: data.get('category'), startDate: data.get('startDate'), scheduledDays };
      await mutate(() => activityId ? api.activities.update(activityId, body) : api.activities.create(body));
      await changed(ctx, activityId ? 'Activity updated.' : 'Activity created.');
    }
  });
}

async function viewActivity(ctx, activityId) {
  const [activity, sessions] = await Promise.all([api.activities.get(activityId, { signal: ctx.signal }), api.sessions.byActivity(activityId, { signal: ctx.signal })]);
  requireCurrentScreen(ctx);
  openDialog({ title: activity.name || `Activity #${activity.id}`, wide: true,
    body: `<div class="detail-grid"><div><span class="eyebrow">Daily target</span><strong>${e(activity.minimumDuration)} minutes</strong></div><div><span class="eyebrow">Category</span><strong>${e(label(activity.category))}</strong></div><div><span class="eyebrow">Start date</span><strong>${e(formatDate(activity.startDate))}</strong></div><div><span class="eyebrow">State</span>${badge(activity.active ? 'Active' : 'Inactive', 'success')}</div><div><span class="eyebrow">Created</span><strong>${e(formatDateTime(activity.createdAt))}</strong></div><div><span class="eyebrow">Last updated</span><strong>${e(formatDateTime(activity.updatedAt))}</strong></div></div><p><span class="eyebrow">Repeat days</span>${e((activity.scheduledDays || []).map(label).join(', '))}</p><h3>Recorded sessions <span class="muted">(${sessions.length})</span></h3>${sessions.length ? `<div class="table-wrap"><table><thead><tr><th>Started</th><th>Finished</th><th>Work time</th></tr></thead><tbody>${sessions.slice().sort((a, b) => String(b.startTime).localeCompare(String(a.startTime))).map(session => `<tr><td>${e(formatDateTime(session.startTime))}</td><td>${e(formatDateTime(session.endTime))}</td><td>${e(formatDuration(session.duration))}</td></tr>`).join('')}</tbody></table></div>` : '<p class="muted">No sessions recorded yet.</p>'}` });
}

async function deleteActivity(ctx, activityId) {
  const activity = await api.activities.get(activityId, { signal: ctx.signal });
  requireCurrentScreen(ctx);
  if (ctx.store.sessions.some(session => session.activityId === activity.id && !session.endTime)) throw new Error('Finish this activity’s running sessions before deleting it.');
  openDialog({ title: 'Delete activity?', body: `<p>Delete <strong>${e(activity.name || `Activity #${activity.id}`)}</strong>? It will be removed from active activities, schedules, progress, and reports. Existing sessions are retained in history.</p><p class="form-note">The backend marks this activity inactive. It does not provide a restore operation.</p>`, submitLabel: 'Delete activity', danger: true,
    onSubmit: async () => { await mutate(() => api.activities.remove(activityId)); await changed(ctx, 'Activity deleted.'); } });
}

async function viewSession(ctx, sessionId) {
  const [session, breaks] = await Promise.all([api.sessions.get(sessionId, { signal: ctx.signal }), api.sessions.breaks(sessionId, { signal: ctx.signal })]);
  requireCurrentScreen(ctx);
  const state = sessionState(session, breaks);
  openDialog({ title: `Session #${session.id}`, wide: true,
    body: `<p class="detail-activity">${e(activityName(ctx.store, session.activityId))} ${badge(state, session.endTime ? 'neutral' : state === 'On break' ? 'warning' : 'success')}</p><div class="detail-grid"><div><span class="eyebrow">Started</span><strong>${e(formatDateTime(session.startTime))}</strong></div><div><span class="eyebrow">Finished</span><strong>${e(formatDateTime(session.endTime))}</strong></div><div><span class="eyebrow">Recorded work time</span><strong>${e(formatDuration(session.duration))}</strong></div><div><span class="eyebrow">Recorded break time</span><strong>${e(formatDuration(breaks.reduce((sum, item) => sum + (item.duration || 0), 0)))}</strong></div></div><h3>Break history</h3>${breaks.length ? `<div class="table-wrap"><table><thead><tr><th>Started</th><th>Resumed</th><th>Duration</th><th>Note</th></tr></thead><tbody>${breaks.slice().sort((a, b) => String(a.startTime).localeCompare(String(b.startTime))).map(item => `<tr><td>${e(formatDateTime(item.startTime))}</td><td>${item.endTime ? e(formatDateTime(item.endTime)) : badge('On break', 'warning')}</td><td>${e(formatDuration(item.duration))}</td><td>${e(item.description || '—')}</td></tr>`).join('')}</tbody></table></div>` : '<p class="muted">No breaks taken in this session.</p>'}${!session.endTime ? '<p class="form-note">Work and break durations are recorded when you finish or resume. The session timer is a local estimate.</p>' : ''}` });
}

async function deleteSession(ctx, sessionId) {
  const session = await api.sessions.get(sessionId, { signal: ctx.signal });
  requireCurrentScreen(ctx);
  openDialog({ title: 'Delete session?', body: `<p>Permanently delete session <strong>#${e(session.id)}</strong> for ${e(activityName(ctx.store, session.activityId))} and all its breaks?</p><p class="form-note">This removes its recorded work from daily progress, streaks, and reports.${!session.endTime ? ' This session is still running.' : ''}</p>`, submitLabel: 'Delete session', danger: true,
    onSubmit: async () => { await mutate(() => api.sessions.remove(sessionId)); await changed(ctx, 'Session deleted.'); } });
}

async function breakSession(ctx, sessionId) {
  const [session, breaks] = await Promise.all([api.sessions.get(sessionId, { signal: ctx.signal }), api.sessions.breaks(sessionId, { signal: ctx.signal })]);
  requireCurrentScreen(ctx);
  if (session.endTime || breaks.some(item => !item.endTime)) throw new Error('This session can no longer start a break. Refresh to see its current state.');
  openDialog({ title: 'Take a break', body: '<p>Your work timer pauses during a break.</p><label class="field">Break note <span class="muted">optional</span><textarea name="description" rows="3" maxlength="255" placeholder="A quick reset, lunch, or another reason"></textarea></label>', submitLabel: 'Start break',
    onSubmit: async data => { await mutate(() => api.sessions.break(sessionId, String(data.get('description') || '').trim())); await changed(ctx, 'Break started.'); } });
}

export function bindActions(ctx) {
  ctx.root.addEventListener('click', event => {
    const button = event.target.closest('[data-action]');
    if (!button || !ctx.root.contains(button)) return;
    const action = button.dataset.action;
    const recordId = Number(button.dataset.id);
    runAction(button, async () => {
      if (action === 'create-activity') return editActivity(ctx);
      if (action === 'edit-activity') return editActivity(ctx, recordId);
      if (action === 'view-activity') return viewActivity(ctx, recordId);
      if (action === 'delete-activity') return deleteActivity(ctx, recordId);
      if (action === 'view-session') return viewSession(ctx, recordId);
      if (action === 'delete-session') return deleteSession(ctx, recordId);
      if (action === 'break-session') return breakSession(ctx, recordId);
      if (action === 'start-session') {
        if (ctx.store.sessions.some(session => session.activityId === recordId && !session.endTime)) throw new Error('This activity already has a running session. Open Sessions to manage it.');
        await mutate(() => api.sessions.start(recordId));
        return changed(ctx, 'Session started.');
      }
      if (action === 'stop-session') { await mutate(() => api.sessions.stop(recordId)); return changed(ctx, 'Session finished. Your recorded progress is updated.'); }
      if (action === 'resume-session') { await mutate(() => api.sessions.resume(recordId)); return changed(ctx, 'Session resumed.'); }
    });
  }, { signal: ctx.signal });
}

