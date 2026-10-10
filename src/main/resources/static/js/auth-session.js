// Tokens are scoped to this backend and this browser tab. Passwords never enter storage.
export const BACKEND_URL = new URL('../', import.meta.url);
const STORAGE_KEY = 'reality.auth.v1';
let epoch = 0;
let session = readSavedSession();

function validSession(value) {
  return value?.backendUrl === BACKEND_URL.href &&
    /^[A-Za-z0-9_-]{43}$/.test(value.token || '') &&
    Number.isFinite(Date.parse(value.expiresAt)) && Date.parse(value.expiresAt) > Date.now() &&
    Number.isSafeInteger(value.user?.id) && value.user.id > 0 &&
    typeof value.user.username === 'string' && typeof value.user.displayName === 'string';
}

function readSavedSession() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || 'null');
    if (validSession(saved)) return saved;
    sessionStorage.removeItem(STORAGE_KEY);
  } catch { /* A private browser may disallow storage; an in-memory session still works. */ }
  return null;
}

function publish(reason = '') {
  window.dispatchEvent(new CustomEvent('reality:session-change', {
    detail: { authenticated: Boolean(session), user: session?.user || null, reason }
  }));
}

export function sessionEpoch() { return epoch; }

export function getSession() {
  if (session && Date.parse(session.expiresAt) <= Date.now()) clearSession('Your session expired. Sign in again.');
  return session;
}

export function saveSession(response) {
  const next = { token: response?.token, expiresAt: response?.expiresAt, user: response?.user, backendUrl: BACKEND_URL.href };
  if (!validSession(next)) throw new Error('The server returned an invalid sign-in response.');
  session = next;
  epoch += 1;
  try { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next)); } catch { /* Keep this session in memory. */ }
  publish();
}

export function clearSession(reason = '') {
  session = null;
  epoch += 1;
  try { sessionStorage.removeItem(STORAGE_KEY); } catch { /* Storage is unavailable. */ }
  publish(reason);
}
