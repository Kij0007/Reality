export function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[character]));
}

export function todayISO() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

export function formatDate(value) {
  if (!value) return '—';
  const date = new Date(`${String(value).slice(0, 10)}T12:00:00`);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

export function formatDateTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export function formatDuration(value) {
  if (value === null || value === undefined) return '—';
  const seconds = Math.max(0, Math.floor(Number(value) || 0));
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  return hours ? `${hours}h ${minutes}m` : minutes ? `${minutes}m ${seconds % 60}s` : `${seconds}s`;
}

export function badge(text, tone = 'neutral') {
  return `<span class="status status-${escapeHtml(tone)}">${escapeHtml(text)}</span>`;
}

export function progressBar(current, target, description = 'Daily target progress') {
  const percent = target > 0 ? Math.min(100, Math.max(0, Number(current) / Number(target) * 100)) : 0;
  return `<div class="progress-track" role="progressbar" aria-label="${escapeHtml(description)}" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${Math.round(percent)}"><span class="progress-fill" style="width:${percent}%"></span></div>`;
}

export function emptyState(title, text, buttonHtml = '') {
  return `<div class="empty-state"><span class="empty-symbol" aria-hidden="true">◇</span><h3>${escapeHtml(title)}</h3><p>${escapeHtml(text)}</p>${buttonHtml}</div>`;
}

export function errorState(message) {
  return `<div class="inline-error" role="alert">${escapeHtml(message)}</div>`;
}

export function loadingState(text = 'Loading your data…') {
  return `<div class="loading-state" role="status"><span class="spinner" aria-hidden="true"></span>${escapeHtml(text)}</div>`;
}

export function activityOptions(activities, selected = '', includeAll = false) {
  return `${includeAll ? '<option value="">All activities</option>' : '<option value="">Choose an activity</option>'}${activities.map(activity => `<option value="${escapeHtml(activity.id)}" ${String(selected) === String(activity.id) ? 'selected' : ''}>${escapeHtml(activity.name || `Activity #${activity.id}`)}</option>`).join('')}`;
}

export function notify(message, type = 'success') {
  const region = document.querySelector('#notifications');
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.setAttribute('role', type === 'error' ? 'alert' : 'status');
  const text = document.createElement('span');
  text.textContent = message;
  const dismiss = document.createElement('button');
  dismiss.type = 'button';
  dismiss.className = 'icon-button';
  dismiss.setAttribute('aria-label', 'Dismiss notification');
  dismiss.textContent = '×';
  dismiss.addEventListener('click', () => toast.remove());
  toast.append(text, dismiss);
  region.append(toast);
  if (type !== 'error') setTimeout(() => toast.remove(), 6000);
}

export async function runAction(button, task, label = 'Working…') {
  if (button?.disabled) return;
  const previous = button?.textContent;
  if (button) { button.disabled = true; button.textContent = label; }
  try { return await task(); }
  catch (error) { if (error.name !== 'AbortError') notify(error.message, 'error'); }
  finally { if (button) { button.disabled = false; button.textContent = previous; } }
}

let dialogController;
export function openDialog({ title, body, submitLabel = 'Save', onSubmit, danger = false, wide = false }) {
  const dialog = document.querySelector('#app-dialog');
  if (dialog.open) return;
  dialogController?.abort();
  dialogController = new AbortController();
  const owner = dialogController;
  const { signal } = owner;
  const previousFocus = document.activeElement;
  dialog.classList.toggle('dialog-wide', wide);
  dialog.innerHTML = `<form id="dialog-form"><div class="dialog-heading"><h2 id="dialog-title">${escapeHtml(title)}</h2><button type="button" class="icon-button" data-close aria-label="Close dialog">×</button></div><fieldset id="dialog-fields">${body}</fieldset><div id="dialog-error" aria-live="assertive"></div><div class="dialog-footer"><button type="button" class="button button-secondary" data-close>${onSubmit ? 'Cancel' : 'Close'}</button>${onSubmit ? `<button type="submit" class="button ${danger ? 'button-danger' : 'button-primary'}">${escapeHtml(submitLabel)}</button>` : ''}</div></form>`;
  const form = dialog.querySelector('form');
  const fields = form.querySelector('#dialog-fields');
  const errorRegion = form.querySelector('#dialog-error');
  let busy = false;
  dialog.querySelectorAll('[data-close]').forEach(button => button.addEventListener('click', () => { if (!busy) dialog.close(); }, { signal }));
  dialog.addEventListener('cancel', event => { if (busy) event.preventDefault(); }, { signal });
  dialog.addEventListener('close', () => { owner.abort(); if (previousFocus?.isConnected) previousFocus.focus(); }, { once: true, signal });
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (!onSubmit || busy || !form.reportValidity()) return;
    const data = new FormData(form);
    const submit = form.querySelector('[type="submit"]');
    busy = true;
    submit.disabled = true;
    submit.textContent = 'Saving…';
    fields.disabled = true;
    errorRegion.innerHTML = '';
    try { await onSubmit(data, form); if (dialogController === owner && dialog.open) dialog.close(); }
    catch (error) { if (dialogController === owner && dialog.open) errorRegion.innerHTML = errorState(error.message); }
    finally { busy = false; submit.disabled = false; submit.textContent = submitLabel; fields.disabled = false; }
  }, { signal });
  dialog.showModal();
}
