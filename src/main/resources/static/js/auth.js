import { api, mutationIsPending, waitForServer } from './api.js';
import { getSession, saveSession, clearSession } from './auth-session.js';
import { notify } from './ui.js';

export function mountAuthentication({ onAuthenticated, onSignedOut }) {
  const screen = document.querySelector('#auth-screen');
  const app = document.querySelector('#authenticated-app');
  const form = document.querySelector('#auth-form');
  const fields = document.querySelector('#auth-fields');
  const password = document.querySelector('#auth-password');
  const displayName = document.querySelector('#auth-display-name');
  const errorRegion = document.querySelector('#auth-error');
  const status = document.querySelector('#auth-status');
  const submit = document.querySelector('#auth-submit');
  const retry = document.querySelector('#auth-retry');
  const logout = document.querySelector('#sign-out');
  let mode = 'login';
  let busy = false;
  let signedIn = false;
  let startupController;

  function showError(message = '') {
    errorRegion.textContent = message;
    errorRegion.hidden = !message;
  }

  function updateBusy() {
    fields.disabled = busy;
    submit.disabled = busy || mutationIsPending();
    retry.disabled = busy;
    logout.disabled = busy || mutationIsPending();
    document.querySelector('#auth-login-tab').disabled = busy;
    document.querySelector('#auth-register-tab').disabled = busy;
    submit.textContent = busy ? 'Connecting…' : mode === 'register' ? 'Create account' : 'Sign in';
  }

  function switchMode(next) {
    if (busy) return;
    mode = next;
    const registering = mode === 'register';
    document.querySelector('#auth-title').textContent = registering ? 'Make room for progress.' : 'Welcome back.';
    document.querySelector('#auth-description').textContent = registering ? 'Create your personal Reality account.' : 'Sign in to your activities, sessions, and progress.';
    document.querySelector('#display-name-field').hidden = !registering;
    displayName.disabled = !registering;
    displayName.required = registering;
    password.autocomplete = registering ? 'new-password' : 'current-password';
    document.querySelector('#password-help').hidden = !registering;
    password.value = '';
    showError();
    for (const [id, active] of [['auth-login-tab', !registering], ['auth-register-tab', registering]]) {
      const tab = document.querySelector(`#${id}`);
      tab.setAttribute('aria-pressed', String(active));
      tab.classList.toggle('button-primary', active);
      tab.classList.toggle('button-secondary', !active);
    }
    updateBusy();
  }

  function showSignedOut(reason = '') {
    signedIn = false;
    screen.hidden = false;
    app.hidden = true;
    document.querySelector('.skip-link').hidden = true;
    document.querySelector('#account-display-name').textContent = '';
    document.querySelector('#account-username').textContent = '';
    password.value = '';
    password.type = 'password';
    document.querySelector('#show-password').checked = false;
    status.textContent = reason || 'Sign in or create an account to continue.';
    showError();
    onSignedOut();
  }

  async function showSignedIn(user) {
    signedIn = true;
    screen.hidden = true;
    app.hidden = false;
    document.querySelector('.skip-link').hidden = false;
    document.querySelector('#account-display-name').textContent = user.displayName;
    document.querySelector('#account-username').textContent = `@${user.username}`;
    password.value = '';
    showError();
    await onAuthenticated();
  }

  async function restoreSession() {
    if (busy) return;
    startupController?.abort();
    startupController = new AbortController();
    const signal = startupController.signal;
    busy = true;
    retry.hidden = true;
    showError();
    updateBusy();
    try {
      await waitForServer({ signal, onStatus: message => { status.textContent = message; } });
      const saved = getSession();
      if (saved) {
        const user = await api.auth.me({ signal });
        if (signal.aborted) return;
        saveSession({ ...saved, user });
        await showSignedIn(user);
      } else showSignedOut();
    } catch (error) {
      if (error.name !== 'AbortError') {
        showSignedOut(error.status === 401 ? 'Your session is no longer valid. Sign in again.' : 'Unable to connect to Reality.');
        if (error.status !== 401) { showError(error.message); retry.hidden = false; }
      }
    } finally {
      busy = false;
      updateBusy();
    }
  }

  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (busy || mutationIsPending() || !form.reportValidity()) return;
    const username = form.elements.username.value.trim().toLowerCase();
    const secret = password.value;
    const name = displayName.value.trim();
    if (!/^[a-z0-9_.-]{3,40}$/.test(username)) { showError('Use 3–40 letters, numbers, dots, underscores, or hyphens for your username.'); return; }
    if (mode === 'register' && (!name || [...name].length > 80)) { showError('Enter a display name of up to 80 characters.'); return; }
    if (new TextEncoder().encode(secret).length > 72 || mode === 'register' && [...secret].length < 12) {
      showError('Use at least 12 characters and at most 72 UTF-8 bytes for your password.');
      return;
    }
    busy = true;
    showError();
    retry.hidden = true;
    updateBusy();
    try {
      await waitForServer({ onStatus: message => { status.textContent = message; } });
      const response = mode === 'register'
        ? await api.auth.register({ username, displayName: name, password: secret })
        : await api.auth.login({ username, password: secret });
      saveSession(response);
      await showSignedIn(response.user);
    } catch (error) {
      showError(error.message);
      status.textContent = 'Check the message above and try again.';
    } finally {
      password.value = '';
      busy = false;
      updateBusy();
    }
  });

  logout.addEventListener('click', async () => {
    if (busy || mutationIsPending()) return;
    busy = true;
    updateBusy();
    logout.textContent = 'Signing out…';
    try {
      await api.auth.logout();
      clearSession('Signed out.');
    } catch (error) {
      if (getSession()) notify(`Sign out could not be confirmed. ${error.message}`, 'error');
    } finally {
      busy = false;
      logout.textContent = 'Sign out';
      updateBusy();
    }
  });

  document.querySelector('#auth-login-tab').addEventListener('click', () => switchMode('login'));
  document.querySelector('#auth-register-tab').addEventListener('click', () => switchMode('register'));
  document.querySelector('#show-password').addEventListener('change', event => { password.type = event.target.checked ? 'text' : 'password'; });
  retry.addEventListener('click', restoreSession);
  window.addEventListener('reality:network-busy', updateBusy);
  window.addEventListener('reality:session-change', event => {
    if (!event.detail.authenticated) showSignedOut(event.detail.reason);
  });
  const expiryCheck = setInterval(() => { if (signedIn) getSession(); }, 30000);
  window.addEventListener('pagehide', () => { startupController?.abort(); clearInterval(expiryCheck); });
  document.querySelector('.skip-link').hidden = true;
  restoreSession();
}
