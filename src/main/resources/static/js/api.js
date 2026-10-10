import { BACKEND_URL, getSession, sessionEpoch, clearSession } from './auth-session.js';

const REQUEST_TIMEOUT = 20000;
const reads = new Set();
let writePending = false;

export class ApiError extends Error {
  constructor(message, status = 0, details = null, uncertainWrite = false) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
    this.uncertainWrite = uncertainWrite;
  }
}

export function mutationIsPending() { return writePending; }
window.addEventListener('reality:session-change', () => {
  reads.forEach(controller => controller.abort('session-changed'));
});

function errorMessage(status, data) {
  // Do not turn a proxy HTML page or an unexpected server stack trace into UI text.
  const message = typeof data === 'string' ? data.trim() : data?.message;
  if (status < 500 && typeof message === 'string' && message.trim() &&
      !/[<>]|(?:java\.|exception|stacktrace)/i.test(message)) return message.slice(0, 600);
  const messages = {
    400: 'Check your input. This action is not valid for the current state.',
    401: 'Your session is no longer valid. Sign in again.',
    403: 'The server did not allow this action.',
    404: 'This record is no longer available. Refresh the page to get the latest data.',
    409: 'This action conflicts with the current data. Refresh and try again.',
    429: 'Too many attempts. Wait a few minutes, then try again.',
    500: 'The server could not complete the request. Please try again.',
    502: 'The Reality server is starting or temporarily unavailable.',
    503: 'The Reality server is starting or temporarily unavailable.',
    504: 'The Reality server took too long to respond.'
  };
  return messages[status] || `The request failed (HTTP ${status}). Please try again.`;
}

async function request(method, path, body, options = {}) {
  const isWrite = method !== 'GET';
  if (isWrite && writePending) throw new ApiError('Another change is being saved. Please wait a moment.');
  const current = options.public ? null : getSession();
  if (!options.public && !current) throw new ApiError('Sign in to use Reality.', 401);
  const epoch = sessionEpoch();
  if (isWrite) {
    writePending = true;
    window.dispatchEvent(new CustomEvent('reality:network-busy', { detail: { busy: true } }));
  }
  const controller = new AbortController();
  if (!isWrite && !options.public) reads.add(controller);
  const timeout = setTimeout(() => controller.abort('timeout'), options.timeout || REQUEST_TIMEOUT);
  const abort = () => controller.abort(options.signal?.reason);
  if (options.signal?.aborted) controller.abort(options.signal.reason);
  else options.signal?.addEventListener('abort', abort, { once: true });
  try {
    // Capture token and address once. A mutation is never retried or redirected.
    const response = await fetch(new URL(path.replace(/^\//, ''), BACKEND_URL), {
      method,
      credentials: 'omit',
      redirect: 'error',
      headers: {
        Accept: 'application/json',
        ...(current ? { Authorization: `Bearer ${current.token}` } : {}),
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' })
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      signal: controller.signal
    });
    const text = response.status === 204 ? '' : await response.text();
    let data = text || null;
    const expectsText = response.ok && options.responseType === 'text';
    if (text && !expectsText && (response.headers.get('content-type')?.includes('json') || /^[\[{]/.test(text.trim()))) {
      try { data = JSON.parse(text); }
      catch {
        if (!response.ok) data = null;
        else throw new ApiError(`The server returned an unreadable response.${isWrite ? ' Refresh before trying this change again.' : ' Please try again.'}`, response.status, null, isWrite);
      }
    }
    if (!response.ok) {
      if (response.status === 401 && !options.public && epoch === sessionEpoch()) clearSession('Your session is no longer valid. Sign in again.');
      const uncertain = isWrite && response.status >= 500;
      throw new ApiError(`${errorMessage(response.status, data)}${uncertain ? ' The change may have been saved; refresh before trying it again.' : ''}`, response.status, data, uncertain);
    }
    if (!options.public && (epoch !== sessionEpoch() || controller.signal.aborted)) throw new DOMException('Request cancelled', 'AbortError');
    return data;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (options.signal?.aborted || controller.signal.reason === 'session-changed' || error.name === 'AbortError' && controller.signal.reason !== 'timeout') throw new DOMException('Request cancelled', 'AbortError');
    const uncertain = isWrite;
    const message = controller.signal.reason === 'timeout' ? 'The Reality server took too long to respond.' : 'Unable to reach the Reality server. Check your connection and try again.';
    throw new ApiError(`${message}${uncertain ? ' The change may have been saved; refresh before trying it again.' : ''}`, 0, null, uncertain);
  } finally {
    clearTimeout(timeout);
    options.signal?.removeEventListener('abort', abort);
    reads.delete(controller);
    if (isWrite) {
      writePending = false;
      window.dispatchEvent(new CustomEvent('reality:network-busy', { detail: { busy: false } }));
    }
  }
}

const id = value => encodeURIComponent(String(value));
export const api = {
  auth: {
    register: body => request('POST', 'api/auth/register', body, { public: true }),
    login: body => request('POST', 'api/auth/login', body, { public: true }),
    me: options => request('GET', 'api/auth/me', undefined, options),
    logout: () => request('POST', 'api/auth/logout')
  },
  health: options => request('GET', 'api/health', undefined, { ...options, public: true }),
  activities: {
    list: options => request('GET', 'activities', undefined, options),
    get: (activityId, options) => request('GET', `activities/${id(activityId)}`, undefined, options),
    create: body => request('POST', 'activities', body),
    update: (activityId, body) => request('PUT', `activities/${id(activityId)}`, body),
    // This controller returns a String body; Spring may label it application/json.
    remove: activityId => request('DELETE', `activities/${id(activityId)}`, undefined, { responseType: 'text' })
  },
  sessions: {
    list: options => request('GET', 'api/sessions', undefined, options),
    get: (sessionId, options) => request('GET', `api/sessions/${id(sessionId)}`, undefined, options),
    byActivity: (activityId, options) => request('GET', `api/sessions/activity/${id(activityId)}`, undefined, options),
    day: (activityId, date, options) => request('GET', `api/sessions/activity/${id(activityId)}/date/${id(date)}`, undefined, options),
    start: activityId => request('POST', 'api/sessions/start', { activityId: Number(activityId) }),
    stop: sessionId => request('PUT', `api/sessions/${id(sessionId)}/stop`),
    remove: sessionId => request('DELETE', `api/sessions/${id(sessionId)}`),
    breaks: (sessionId, options) => request('GET', `api/sessions/${id(sessionId)}/breaks`, undefined, options),
    break: (sessionId, description = '') => request('POST', `api/sessions/${id(sessionId)}/break`, { description }),
    resume: sessionId => request('PUT', `api/sessions/${id(sessionId)}/resume`)
  },
  progress: { day: (activityId, date, options) => request('GET', `api/daily-progress/activity/${id(activityId)}/date/${id(date)}`, undefined, options) },
  streaks: { get: (activityId, options) => request('GET', `api/streaks/activity/${id(activityId)}`, undefined, options) },
  reports: { month: (activityId, month, options) => request('GET', `api/reports/activity/${id(activityId)}/month/${id(month)}`, undefined, options) }
};

function pause(milliseconds, signal) {
  return new Promise((resolve, reject) => {
    const abort = () => { clearTimeout(timer); reject(new DOMException('Request cancelled', 'AbortError')); };
    const timer = setTimeout(() => { signal?.removeEventListener('abort', abort); resolve(); }, milliseconds);
    if (signal?.aborted) abort(); else signal?.addEventListener('abort', abort, { once: true });
  });
}

// Free hosting can sleep. Only this public, read-only health request is retried.
export async function waitForServer({ signal, onStatus = () => {} } = {}) {
  const deadline = Date.now() + 120000;
  while (Date.now() < deadline) {
    onStatus('Connecting to Reality. A sleeping server may take up to two minutes to wake…');
    try {
      const result = await api.health({ signal, timeout: Math.min(15000, deadline - Date.now()) });
      if (result?.status !== 'UP') throw new ApiError('The backend URL did not return the Reality health response.', 200);
      onStatus('Reality is ready.');
      return;
    } catch (error) {
      if (error.name === 'AbortError') throw error;
      if (error.status && ![502, 503, 504].includes(error.status)) throw error;
      if (Date.now() >= deadline) break;
      await pause(Math.min(2000, deadline - Date.now()), signal);
    }
  }
  throw new ApiError('Reality is still unavailable. Check your connection, then select Retry connection.');
}
