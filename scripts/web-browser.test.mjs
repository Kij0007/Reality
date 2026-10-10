import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

// This test drives the real shipped web UI against a disposable database.
// It neither intercepts API requests nor seeds mocked application records.
const args = process.argv.slice(2);
const option = name => args[args.indexOf(name) + 1];
if (!args.includes('--allow-create-test-data')) throw new Error('Explicit --allow-create-test-data is required. Use a disposable test database.');
if (!args.includes('--base-url')) throw new Error('--base-url is required.');
const base = new URL(option('--base-url'));
if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password || base.search || base.hash) throw new Error('Invalid test backend URL.');
if (base.protocol === 'http:' && !['127.0.0.1', 'localhost', '[::1]'].includes(base.hostname)) throw new Error('HTTP browser tests must use loopback.');
if (!base.pathname.endsWith('/')) base.pathname += '/';
const modulePath = process.env.REALITY_PLAYWRIGHT_MODULE;
if (!modulePath) throw new Error('Set REALITY_PLAYWRIGHT_MODULE to the CI-only Playwright index.mjs file.');
const { chromium } = await import(pathToFileURL(resolve(modulePath)).href);
const output = resolve(args.includes('--output-dir') ? option('--output-dir') : 'web-browser-results');
await mkdir(output, { recursive: true });
const suffix = `${Date.now().toString(36)}${randomBytes(3).toString('hex')}`;
const usernameA = `browser_${suffix}_a`;
const usernameB = `browser_${suffix}_b`;
const password = `BrowserOnly!${randomBytes(18).toString('base64url')}`;
const originalName = `Browser commitment ${suffix}`;
const editedName = `Updated commitment ${suffix}`;
const evidence = { baseUrl: base.href, startedAt: new Date().toISOString(), status: 'RUNNING', steps: [], requests: [], consoleErrors: [], pageErrors: [] };
const redact = value => String(value).replaceAll(password, '[redacted]').replace(/Bearer\s+[A-Za-z0-9_.-]+/g, 'Bearer [redacted]');
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, timezoneId: 'Asia/Kolkata', locale: 'en-IN' });
const page = await context.newPage();
page.setDefaultTimeout(15000);
page.on('pageerror', error => evidence.pageErrors.push(redact(error.message)));
page.on('console', message => { if (message.type() === 'error') evidence.consoleErrors.push(redact(message.text())); });
page.on('response', response => {
  const url = new URL(response.url());
  if (url.origin === base.origin) evidence.requests.push({ method: response.request().method(), path: url.pathname, status: response.status() });
});

async function until(check, description) {
  const deadline = Date.now() + 15000;
  while (Date.now() < deadline) {
    if (await check()) return;
    await new Promise(done => setTimeout(done, 75));
  }
  throw new Error(`Timed out waiting for ${description}.`);
}

async function contains(locator, value) {
  await locator.waitFor({ state: 'visible' });
  // innerText reflects CSS text-transform; presentation casing is not a contract.
  await until(async () => (await locator.innerText()).toLowerCase().includes(value.toLowerCase()), value);
}

async function screenshot(name) {
  await page.screenshot({ path: resolve(output, `${name}.png`), fullPage: true });
}

async function step(name, action) {
  try {
    await action();
    evidence.steps.push({ name, status: 'PASS' });
    console.log(`PASS: ${name}`);
  } catch (error) {
    evidence.steps.push({ name, status: 'FAIL', message: redact(error.message) });
    throw error;
  }
}

async function serverAction(method, path, action, expectedStatus = 200) {
  const expected = new URL(path.replace(/^\//, ''), base).pathname;
  const result = page.waitForResponse(response => response.request().method() === method && new URL(response.url()).pathname === expected);
  await action();
  const response = await result;
  assert.equal(response.status(), expectedStatus, `${method} ${path}`);
  if (expectedStatus === 204 || method === 'DELETE') return null;
  return response.json();
}

async function navigate(route) {
  await page.locator(`#main-navigation a[href="#${route}"]`).click();
  await contains(page.locator('#page-title'), { dashboard: 'Dashboard', activities: 'Activities', schedule: 'Schedule', sessions: 'Sessions', progress: 'Progress', reports: 'Reports' }[route]);
}

async function register(username, displayName) {
  await page.locator('#auth-register-tab').click();
  await page.locator('#auth-username').fill(username);
  await page.locator('#auth-display-name').fill(displayName);
  await page.locator('#auth-password').fill(password);
  const result = await serverAction('POST', 'api/auth/register', () => page.locator('#auth-submit').click(), 201);
  assert.equal(result.user.username, username);
  await page.locator('#authenticated-app').waitFor({ state: 'visible' });
  await contains(page.locator('#connection-status'), 'Connected');
  await contains(page.locator('#account-display-name'), displayName);
}

async function signOut() {
  await serverAction('POST', 'api/auth/logout', () => page.locator('#sign-out').click(), 204);
  await page.locator('#auth-screen').waitFor({ state: 'visible' });
  assert.equal(await page.locator('#authenticated-app').isVisible(), false);
  assert.equal(await page.locator('#view').innerText(), '');
}

let activityId;
let sessionId;
try {
  await step('Real static resources load; unauthenticated dashboard stays hidden', async () => {
    const response = await page.goto(base.href, { waitUntil: 'networkidle' });
    assert.equal(response.status(), 200);
    await page.locator('#auth-screen').waitFor({ state: 'visible' });
    await until(() => page.locator('#auth-submit').isEnabled(), 'server readiness');
    assert.equal(await page.locator('#authenticated-app').isVisible(), false);
    await screenshot('01-sign-in');
  });
  await step('Registration rejects a short password before any server write', async () => {
    await page.locator('#auth-register-tab').click();
    await page.locator('#auth-username').fill(usernameA);
    await page.locator('#auth-display-name').fill('Browser account A');
    await page.locator('#auth-password').fill('short');
    const writesBefore = evidence.requests.filter(item => item.method === 'POST').length;
    await page.locator('#auth-submit').click();
    await contains(page.locator('#auth-error'), 'at least 12');
    assert.equal(evidence.requests.filter(item => item.method === 'POST').length, writesBefore);
  });
  await step('Register account A and load the empty real dataset', async () => {
    await register(usernameA, 'Browser account A');
    await navigate('activities');
    await contains(page.locator('#activity-list'), 'Your first commitment starts here');
  });
  await step('Create an activity with the exact category, duration, date, and weekday fields', async () => {
    await page.locator('.section-heading [data-action="create-activity"]').click();
    const dialog = page.locator('#app-dialog');
    await dialog.waitFor({ state: 'visible' });
    await dialog.locator('input[name="name"]').fill(originalName);
    await dialog.locator('input[name="minimumDuration"]').fill('1');
    await dialog.locator('select[name="category"]').selectOption('FITNESS');
    assert.match(await dialog.locator('input[name="startDate"]').inputValue(), /^\d{4}-\d{2}-\d{2}$/);
    const created = await serverAction('POST', 'activities', () => dialog.locator('button[type="submit"]').click(), 201);
    activityId = created.id;
    assert.ok(Number.isInteger(activityId) && activityId > 0);
    assert.equal(created.category, 'FITNESS');
    assert.equal(created.minimumDuration, 1);
    assert.equal(created.scheduledDays.length, 7);
    await dialog.waitFor({ state: 'hidden' });
    await contains(page.locator('#activity-list'), originalName);
  });
  await step('Read activity details and edit the persisted activity', async () => {
    await page.locator('.activity-card [data-action="view-activity"]').click();
    await contains(page.locator('#app-dialog'), 'Recorded sessions');
    await page.locator('#app-dialog .dialog-footer [data-close]').click();
    await page.locator('.activity-card [data-action="edit-activity"]').click();
    const dialog = page.locator('#app-dialog');
    await dialog.locator('input[name="name"]').fill(editedName);
    const updated = await serverAction('PUT', `activities/${activityId}`, () => dialog.locator('button[type="submit"]').click());
    assert.equal(updated.name, editedName);
    await dialog.waitFor({ state: 'hidden' });
    await contains(page.locator('#activity-list'), editedName);
  });
  await step('Schedule and activity search/category filters use the saved activity', async () => {
    await page.locator('#activity-search').fill('no matching commitment');
    await contains(page.locator('#activity-list'), 'No matching activities');
    await page.locator('#activity-search').fill('');
    await page.locator('#category-filter').selectOption('STUDY');
    await contains(page.locator('#activity-list'), 'No matching activities');
    await page.locator('#category-filter').selectOption('FITNESS');
    await contains(page.locator('#activity-list'), editedName);
    await navigate('schedule');
    await contains(page.locator('#schedule-day'), editedName);
    await screenshot('02-schedule');
  });
  await step('Start a real session and expose only valid running controls', async () => {
    await navigate('activities');
    const session = await serverAction('POST', 'api/sessions/start', () => page.locator('.activity-card [data-action="start-session"]').click(), 201);
    sessionId = session.id;
    assert.equal(session.activityId, activityId);
    await navigate('sessions');
    await contains(page.locator('.running-card'), editedName);
    assert.equal(await page.locator('.running-card [data-action="resume-session"]').count(), 0);
    await page.locator('.running-card [data-action="break-session"]').waitFor({ state: 'visible' });
  });
  await step('Start a break, display its server state, and resume', async () => {
    await page.locator('.running-card [data-action="break-session"]').click();
    const dialog = page.locator('#app-dialog');
    await dialog.locator('textarea[name="description"]').fill('Disposable browser verification break');
    await serverAction('POST', `api/sessions/${sessionId}/break`, () => dialog.locator('button[type="submit"]').click(), 201);
    await dialog.waitFor({ state: 'hidden' });
    await contains(page.locator('.running-card'), 'On break');
    assert.equal(await page.locator('.running-card [data-action="break-session"]').count(), 0);
    await screenshot('03-break');
    await serverAction('PUT', `api/sessions/${sessionId}/resume`, () => page.locator('.running-card [data-action="resume-session"]').click());
    await contains(page.locator('.running-card'), 'Running');
    await page.locator('.running-card [data-action="break-session"]').waitFor({ state: 'visible' });
  });
  await step('Finish, inspect break history, and filter completed sessions', async () => {
    const stopped = await serverAction('PUT', `api/sessions/${sessionId}/stop`, () => page.locator('.running-card [data-action="stop-session"]').click());
    assert.ok(stopped.endTime);
    await contains(page.locator('#history-results'), 'Completed');
    await page.locator('#history-results [data-action="view-session"]').click();
    await contains(page.locator('#app-dialog'), 'Disposable browser verification break');
    await page.locator('#app-dialog .dialog-footer [data-close]').click();
    await page.locator('#history-filter select[name="activity"]').selectOption(String(activityId));
    await page.locator('#history-filter select[name="status"]').selectOption('completed');
    await page.locator('#history-filter button[type="submit"]').click();
    await contains(page.locator('#history-results'), `Session #${sessionId}`);
  });
  await step('Daily progress, day sessions, streaks, and monthly reports render real responses', async () => {
    await navigate('progress');
    await contains(page.locator('[data-progress-result]'), 'Current streak');
    await contains(page.locator('[data-progress-result]'), editedName);
    await contains(page.locator('[data-progress-result]'), 'completed sessions');
    await navigate('reports');
    await contains(page.locator('[data-report-result]'), 'Monthly report');
    await contains(page.locator('[data-report-result]'), editedName);
    await contains(page.locator('[data-report-result]'), 'Best streak this month');
    await screenshot('04-report');
  });
  await step('Sign out clears all account A data; account B receives an empty isolated dataset', async () => {
    await signOut();
    await register(usernameB, 'Browser account B');
    await contains(page.locator('#view'), 'No reports yet');
    await navigate('activities');
    await contains(page.locator('#activity-list'), 'Your first commitment starts here');
    assert.equal((await page.locator('#authenticated-app').innerText()).includes(editedName), false);
    await navigate('sessions');
    await contains(page.locator('#history-results'), 'No sessions match these filters');
    await screenshot('05-account-isolation');
    await signOut();
  });
  await step('Sign in to account A and restore its persisted data after a browser reload', async () => {
    await page.locator('#auth-login-tab').click();
    await page.locator('#auth-username').fill(usernameA);
    await page.locator('#auth-password').fill(password);
    await serverAction('POST', 'api/auth/login', () => page.locator('#auth-submit').click());
    await page.locator('#authenticated-app').waitFor({ state: 'visible' });
    await navigate('activities');
    await contains(page.locator('#activity-list'), editedName);
    const restored = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/api/auth/me'));
    await page.reload({ waitUntil: 'networkidle' });
    assert.equal((await restored).status(), 200);
    await contains(page.locator('#account-username'), `@${usernameA}`);
    await contains(page.locator('#activity-list'), editedName);
  });
  await step('Disposable session and activity deletion require confirmation and refresh the real UI', async () => {
    await navigate('sessions');
    await page.locator('#history-results [data-action="delete-session"]').click();
    await contains(page.locator('#app-dialog'), 'Delete session?');
    assert.equal(await page.locator('#history-results [data-action="delete-session"]').count(), 1);
    await serverAction('DELETE', `api/sessions/${sessionId}`, () => page.locator('#app-dialog button[type="submit"]').click(), 204);
    await page.locator('#app-dialog').waitFor({ state: 'hidden' });
    await contains(page.locator('#history-results'), 'No sessions match these filters');
    await navigate('activities');
    await page.locator('.activity-card [data-action="delete-activity"]').click();
    await contains(page.locator('#app-dialog'), 'Delete activity?');
    await serverAction('DELETE', `activities/${activityId}`, () => page.locator('#app-dialog button[type="submit"]').click());
    await page.locator('#app-dialog').waitFor({ state: 'hidden' });
    await contains(page.locator('#activity-list'), 'Your first commitment starts here');
  });
  await step('Phone-sized navigation remains usable', async () => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.locator('#menu-toggle').click();
    await navigate('dashboard');
    assert.equal(await page.locator('#menu-toggle').getAttribute('aria-expanded'), 'false');
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), 'Mobile page overflows horizontally');
    await screenshot('06-mobile-dashboard');
    await page.setViewportSize({ width: 1440, height: 1000 });
    await signOut();
  });
  await step('No JavaScript errors, console errors, broken assets, or failed API responses', async () => {
    assert.deepEqual(evidence.pageErrors, []);
    assert.deepEqual(evidence.consoleErrors, []);
    assert.deepEqual(evidence.requests.filter(item => item.status >= 400), []);
    const calls = evidence.requests;
    const required = [
      ['GET', /^\/api\/health$/], ['POST', /^\/api\/auth\/register$/], ['POST', /^\/api\/auth\/login$/], ['GET', /^\/api\/auth\/me$/], ['POST', /^\/api\/auth\/logout$/],
      ['GET', /^\/activities$/], ['GET', /^\/activities\/\d+$/], ['POST', /^\/activities$/], ['PUT', /^\/activities\/\d+$/], ['DELETE', /^\/activities\/\d+$/],
      ['GET', /^\/api\/sessions$/], ['GET', /^\/api\/sessions\/\d+$/], ['POST', /^\/api\/sessions\/start$/], ['PUT', /^\/api\/sessions\/\d+\/stop$/], ['DELETE', /^\/api\/sessions\/\d+$/],
      ['GET', /^\/api\/sessions\/activity\/\d+$/], ['GET', /^\/api\/sessions\/activity\/\d+\/date\/\d{4}-\d{2}-\d{2}$/],
      ['GET', /^\/api\/sessions\/\d+\/breaks$/], ['POST', /^\/api\/sessions\/\d+\/break$/], ['PUT', /^\/api\/sessions\/\d+\/resume$/],
      ['GET', /^\/api\/daily-progress\/activity\/\d+\/date\/\d{4}-\d{2}-\d{2}$/], ['GET', /^\/api\/streaks\/activity\/\d+$/], ['GET', /^\/api\/reports\/activity\/\d+\/month\/\d{4}-\d{2}$/]
    ];
    for (const [method, pattern] of required) {
      assert.ok(calls.some(call => call.method === method && pattern.test(`/${call.path.slice(base.pathname.length)}`)), `UI did not exercise ${method} ${pattern}`);
    }
    evidence.coveredEndpointCount = required.length;
  });
  evidence.status = 'PASS';
} catch (error) {
  evidence.status = 'FAIL';
  evidence.failure = redact(error.message);
  try { await screenshot('failure'); } catch { /* Preserve the original failure. */ }
  console.error(`FAIL: ${redact(error.message)}`);
  process.exitCode = 1;
} finally {
  evidence.finishedAt = new Date().toISOString();
  await writeFile(resolve(output, 'results.json'), `${JSON.stringify(evidence, null, 2)}\n`);
  await browser.close();
}