import { store, loadStore, clearStore } from './state.js';
import { mountAuthentication } from './auth.js';
import { getSession, sessionEpoch } from './auth-session.js';
import { bindActions } from './actions.js';
import { updateTimers } from './sessions.js';
import { loadingState, errorState } from './ui.js';
import * as dashboard from './dashboard.js';
import * as activities from './activities.js';
import * as schedule from './schedule.js';
import * as sessions from './sessions.js';
import * as progress from './progress.js';
import * as reports from './reports.js';

const pages = { dashboard, activities, schedule, sessions, progress, reports };
const titles = { dashboard: 'Dashboard', activities: 'Activities', schedule: 'Schedule', sessions: 'Sessions', progress: 'Progress & streaks', reports: 'Reports' };
const root = document.querySelector('#view');
const refreshButton = document.querySelector('#refresh-data');
let routeController;
let loadController;
let currentContext;
let hasLoaded = false;
let refreshing = false;
let queuedRefresh = false;
let refreshWaiters = [];
let authenticated = false;

function discardAccountState() {
  authenticated = false;
  routeController?.abort();
  loadController?.abort();
  routeController = null;
  loadController = null;
  currentContext = null;
  hasLoaded = false;
  refreshing = false;
  queuedRefresh = false;
  refreshWaiters.splice(0).forEach(resolve => resolve(false));
  clearStore();
  Object.values(pages).forEach(page => page.reset?.());
  root.replaceChildren();
  document.querySelector('#connection-error').replaceChildren();
  document.querySelector('#notifications').replaceChildren();
  document.querySelector('#nav-activity-count').textContent = '';
  document.querySelector('#nav-session-count').textContent = '';
  document.querySelector('#last-updated').textContent = 'Your data, your progress.';
  const dialog = document.querySelector('#app-dialog');
  if (dialog.open) dialog.close();
  dialog.replaceChildren();
  closeNavigation();
}

function closeNavigation() {
  document.body.classList.remove('navigation-open');
  document.querySelector('#menu-toggle').setAttribute('aria-expanded', 'false');
  document.querySelector('#navigation-backdrop').hidden = true;
}

function currentPage() {
  const hash = window.location.hash.slice(1);
  return pages[hash] ? hash : 'dashboard';
}

async function renderPage({ navigation = false } = {}) {
  routeController?.abort();
  routeController = new AbortController();
  const page = currentPage();
  const signal = routeController.signal;
  document.title = `Reality · ${titles[page]}`;
  document.querySelector('#page-title').textContent = titles[page];
  document.querySelectorAll('[data-page]').forEach(link => {
    const active = link.dataset.page === page;
    link.classList.toggle('is-active', active);
    if (active) link.setAttribute('aria-current', 'page'); else link.removeAttribute('aria-current');
  });
  closeNavigation();
  if (navigation) {
    const dialog = document.querySelector('#app-dialog');
    if (dialog.open) dialog.close();
    document.querySelector('#main-content').focus({ preventScroll: true });
    window.scrollTo({ top: 0 });
  }
  if (!authenticated || !hasLoaded) return;
  root.innerHTML = loadingState();
  const context = { root, store, signal, navigate: hash => { window.location.hash = hash.replace(/^#/, ''); }, refresh };
  currentContext = context;
  bindActions(context);
  try { await pages[page].render(context); }
  catch (error) { if (!signal.aborted) root.innerHTML = errorState(error.message) + '<button class="button button-secondary retry-button" data-retry>Try again</button>'; }
}

async function refresh({ silent = false } = {}) {
  if (!authenticated || !getSession()) return false;
  if (refreshing) {
    queuedRefresh = true;
    return new Promise(resolve => refreshWaiters.push(resolve));
  }
  refreshing = true;
  loadController = new AbortController();
  const owner = loadController;
  const epoch = sessionEpoch();
  const status = document.querySelector('#connection-status');
  refreshButton.disabled = true;
  refreshButton.textContent = 'Refreshing…';
  if (!silent) status.innerHTML = '<span class="connection-dot pending"></span>Loading data';
  try {
    await loadStore(owner.signal);
    if (owner.signal.aborted || epoch !== sessionEpoch() || !authenticated) return false;
    hasLoaded = true;
    status.innerHTML = '<span class="connection-dot"></span>Connected';
    document.querySelector('#connection-error').innerHTML = '';
    document.querySelector('#nav-activity-count').textContent = store.activities.length;
    const activeCount = store.sessions.filter(session => !session.endTime).length;
    document.querySelector('#nav-session-count').textContent = activeCount || '';
    document.querySelector('#last-updated').textContent = `Updated ${new Date().toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}`;
    await renderPage();
    return true;
  } catch (error) {
    if (owner.signal.aborted || epoch !== sessionEpoch() || !authenticated || error.name === 'AbortError') return false;
    status.innerHTML = '<span class="connection-dot offline"></span>Connection issue';
    document.querySelector('#connection-error').innerHTML = errorState(`${error.message}${hasLoaded ? ' You are viewing the last loaded data.' : ''}`);
    if (!hasLoaded) root.innerHTML = `<div class="panel">${errorState(error.message)}<p class="muted">Serve these files from the existing Spring Boot application so its API routes are available.</p><button class="button button-primary retry-button" data-retry>Retry connection</button></div>`;
    return false;
  } finally {
    if (loadController === owner) {
      refreshing = false;
      refreshButton.disabled = false;
      refreshButton.textContent = 'Refresh data';
      if (queuedRefresh) {
        queuedRefresh = false;
        const waiters = refreshWaiters;
        refreshWaiters = [];
        refresh().then(result => waiters.forEach(resolve => resolve(result)));
      }
    }
  }
}

refreshButton.addEventListener('click', () => refresh());
document.querySelector('.skip-link').addEventListener('click', event => {
  event.preventDefault();
  document.querySelector('#main-content').focus();
});
root.addEventListener('click', event => { if (event.target.closest('[data-retry]')) refresh(); });
window.addEventListener('hashchange', () => renderPage({ navigation: true }));
document.querySelector('#menu-toggle').addEventListener('click', () => {
  const open = document.body.classList.toggle('navigation-open');
  document.querySelector('#menu-toggle').setAttribute('aria-expanded', String(open));
  document.querySelector('#navigation-backdrop').hidden = !open;
});
document.querySelector('#navigation-backdrop').addEventListener('click', closeNavigation);
document.addEventListener('keydown', event => { if (event.key === 'Escape') closeNavigation(); });
setInterval(() => { if (currentContext && !currentContext.signal.aborted) updateTimers(currentContext); }, 1000);
// Refresh running sessions without interrupting a form or an open dialog.
setInterval(() => {
  if (authenticated && hasLoaded && !refreshing && !document.hidden && !document.querySelector('#app-dialog').open && !document.activeElement?.matches('input,select,textarea') && store.sessions.some(session => !session.endTime)) refresh({ silent: true });
}, 15000);
window.addEventListener('pagehide', () => { routeController?.abort(); loadController?.abort(); });
renderPage();
mountAuthentication({
  onSignedOut: discardAccountState,
  onAuthenticated: async () => {
    authenticated = true;
    root.innerHTML = loadingState();
    await refresh();
  }
});
