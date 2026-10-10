import test, { beforeEach } from 'node:test';
import assert from 'node:assert/strict';

// Production uses browser globals. This test double stores only synthetic tokens.
globalThis.window = new EventTarget();
const storage = new Map();
globalThis.sessionStorage = {
  getItem: key => storage.get(key) ?? null,
  setItem: (key, value) => storage.set(key, value),
  removeItem: key => storage.delete(key)
};
const { api, ApiError, mutationIsPending, waitForServer } = await import('../src/main/resources/static/js/api.js');
const { BACKEND_URL, saveSession, clearSession, getSession } = await import('../src/main/resources/static/js/auth-session.js');
const { store, loadStore, clearStore } = await import('../src/main/resources/static/js/state.js');
const token = 'a'.repeat(43);
const user = { id: 1, username: 'test_alice', displayName: 'Test Alice', createdAt: '2026-01-01T00:00:00Z' };
const authResponse = { token, expiresAt: new Date(Date.now() + 86400000).toISOString(), user };
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const relativePath = url => new URL(url).pathname.slice(BACKEND_URL.pathname.length);

beforeEach(() => { clearSession(); clearStore(); saveSession(authResponse); });

test('all 18 existing API contracts preserve methods, bodies, paths, bearer token and empty/text deletes', async () => {
  const activity = { name: 'Test activity', minimumDuration: 30, category: 'STUDY', scheduledDays: ['MONDAY'], startDate: '2026-10-12' };
  const contracts = [
    [() => api.activities.list(), 'GET', 'activities'],
    [() => api.activities.get(7), 'GET', 'activities/7'],
    [() => api.activities.create(activity), 'POST', 'activities', activity, 201],
    [() => api.activities.update(7, activity), 'PUT', 'activities/7', activity],
    [() => api.activities.remove(7), 'DELETE', 'activities/7', undefined, 200, 'deleted'],
    [() => api.sessions.list(), 'GET', 'api/sessions'],
    [() => api.sessions.get(9), 'GET', 'api/sessions/9'],
    [() => api.sessions.byActivity(7), 'GET', 'api/sessions/activity/7'],
    [() => api.sessions.day(7, '2026-10-12'), 'GET', 'api/sessions/activity/7/date/2026-10-12'],
    [() => api.sessions.start(7), 'POST', 'api/sessions/start', { activityId: 7 }, 201],
    [() => api.sessions.stop(9), 'PUT', 'api/sessions/9/stop'],
    [() => api.sessions.remove(9), 'DELETE', 'api/sessions/9', undefined, 204],
    [() => api.sessions.breaks(9), 'GET', 'api/sessions/9/breaks'],
    [() => api.sessions.break(9, 'Lunch'), 'POST', 'api/sessions/9/break', { description: 'Lunch' }, 201],
    [() => api.sessions.resume(9), 'PUT', 'api/sessions/9/resume'],
    [() => api.progress.day(7, '2026-10-12'), 'GET', 'api/daily-progress/activity/7/date/2026-10-12'],
    [() => api.streaks.get(7), 'GET', 'api/streaks/activity/7'],
    [() => api.reports.month(7, '2026-10'), 'GET', 'api/reports/activity/7/month/2026-10']
  ];
  for (const [action, method, path, body, status = 200, text] of contracts) {
    let calls = 0;
    globalThis.fetch = async (url, options) => {
      calls += 1;
      assert.equal(relativePath(url), path);
      assert.equal(options.method, method);
      assert.equal(options.headers.Authorization, `Bearer ${token}`);
      assert.equal(options.credentials, 'omit');
      assert.equal(options.redirect, 'error');
      assert.deepEqual(options.body === undefined ? undefined : JSON.parse(options.body), body);
      assert.equal(options.headers['Content-Type'], body === undefined ? undefined : 'application/json');
      if (status === 204) return new Response(null, { status });
      return text ? new Response(text, { status, headers: { 'Content-Type': 'application/json' } }) : json({ id: 7 }, status);
    };
    const result = await action();
    if (text) assert.equal(result, text);
    if (status === 204) assert.equal(result, null);
    assert.equal(calls, 1);
  }
});

test('authentication and health use exact public/protected contracts without password persistence', async () => {
  const register = { username: 'test_alice', displayName: 'Test Alice', password: 'test password only' };
  const contracts = [
    [() => api.auth.register(register), 'POST', 'api/auth/register', register, false, 201],
    [() => api.auth.login({ username: register.username, password: register.password }), 'POST', 'api/auth/login', { username: register.username, password: register.password }, false],
    [() => api.auth.me(), 'GET', 'api/auth/me', undefined, true],
    [() => api.auth.logout(), 'POST', 'api/auth/logout', undefined, true, 204],
    [() => api.health(), 'GET', 'api/health', undefined, false]
  ];
  for (const [action, method, path, body, authenticated, status = 200] of contracts) {
    globalThis.fetch = async (url, options) => {
      assert.equal(relativePath(url), path);
      assert.equal(options.method, method);
      assert.equal(options.headers.Authorization, authenticated ? `Bearer ${token}` : undefined);
      assert.deepEqual(options.body === undefined ? undefined : JSON.parse(options.body), body);
      return status === 204 ? new Response(null, { status }) : json(path.endsWith('/me') ? user : path.endsWith('/health') ? { status: 'UP' } : authResponse, status);
    };
    await action();
  }
  assert.equal([...storage.values()].some(value => value.includes(register.password)), false);
});

test('401 clears authentication and prevents queued reads from publishing old-account records', async () => {
  let finishOldRead;
  globalThis.fetch = async (url) => relativePath(url) === 'activities'
    ? new Promise(resolve => { finishOldRead = resolve; })
    : json({ message: 'Sign in to use Reality.' }, 401);
  const oldRead = api.activities.list();
  await assert.rejects(api.auth.me(), error => error.status === 401);
  assert.equal(getSession(), null);
  assert.equal(storage.size, 0);
  saveSession({ ...authResponse, token: 'b'.repeat(43), user: { ...user, id: 2 } });
  finishOldRead(json([{ id: 7, name: 'Alice private activity' }]));
  await assert.rejects(oldRead, error => error.name === 'AbortError');
});

test('store snapshot is rejected when the account changes before related reads finish', async () => {
  let finishBreaks;
  globalThis.fetch = async url => {
    const path = relativePath(url);
    if (path === 'activities') return json([{ id: 7, name: 'Alice private activity' }]);
    if (path === 'api/sessions') return json([{ id: 9, activityId: 7, startTime: '2026-10-12T09:00:00', endTime: null }]);
    return new Promise(resolve => { finishBreaks = resolve; });
  };
  const loading = loadStore();
  while (!finishBreaks) await new Promise(resolve => setImmediate(resolve));
  clearSession();
  clearStore();
  saveSession({ ...authResponse, token: 'b'.repeat(43), user: { ...user, id: 2 } });
  finishBreaks(json([]));
  await assert.rejects(loading, error => error.name === 'AbortError');
  assert.deepEqual(store.activities, []);
  assert.deepEqual(store.sessions, []);
});

test('duplicate mutations are rejected and ambiguous network failures are never retried', async () => {
  let finish;
  let calls = 0;
  globalThis.fetch = async () => { calls += 1; return new Promise(resolve => { finish = resolve; }); };
  const starting = api.sessions.start(7);
  assert.equal(mutationIsPending(), true);
  await assert.rejects(api.sessions.start(7), /Another change/);
  assert.equal(calls, 1);
  finish(json({ id: 9 }, 201));
  await starting;
  assert.equal(mutationIsPending(), false);
  globalThis.fetch = async () => { calls += 1; throw new TypeError('Network failure'); };
  await assert.rejects(api.sessions.stop(9), error => error instanceof ApiError && error.uncertainWrite && /may have been saved/.test(error.message));
  assert.equal(calls, 2);
  assert.equal(mutationIsPending(), false);
});

test('validation errors remain readable; malformed success and server failures cannot imply safe retries', async () => {
  globalThis.fetch = async () => json({ status: 400, message: 'An active break already exists.' }, 400);
  await assert.rejects(api.sessions.break(9), error => error.status === 400 && !error.uncertainWrite && error.message === 'An active break already exists.');
  globalThis.fetch = async () => new Response('{ invalid json', { status: 201, headers: { 'Content-Type': 'application/json' } });
  await assert.rejects(api.sessions.start(7), error => error.uncertainWrite && /Refresh/.test(error.message));
  globalThis.fetch = async () => json({ message: 'java.lang.SecretException: private database detail' }, 500);
  await assert.rejects(api.sessions.stop(9), error => error.uncertainWrite && !error.message.includes('SecretException'));
});

test('health wake-up retries only public reads and rejects a wrong successful backend response', async () => {
  let calls = 0;
  globalThis.fetch = async (url, options) => {
    assert.equal(relativePath(url), 'api/health');
    assert.equal(options.method, 'GET');
    assert.equal(options.headers.Authorization, undefined);
    calls += 1;
    return calls === 1 ? new Response('Starting', { status: 503 }) : json({ status: 'UP' });
  };
  await waitForServer();
  assert.equal(calls, 2);
  globalThis.fetch = async () => json({ app: 'different service' });
  await assert.rejects(waitForServer(), /did not return the Reality health response/);
});

test('invalid or expired server-generated sessions cannot be saved', () => {
  assert.throws(() => saveSession({ ...authResponse, token: 'short' }), /invalid sign-in/);
  assert.throws(() => saveSession({ ...authResponse, expiresAt: '2020-01-01T00:00:00Z' }), /invalid sign-in/);
});
