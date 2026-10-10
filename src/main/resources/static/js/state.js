import { api } from './api.js';
import { sessionEpoch } from './auth-session.js';

// Only transient server snapshots live here. Application records are never stored locally.
export const store = { activities: [], sessions: [], breaks: new Map(), breakErrors: new Map() };

export function clearStore() {
  Object.assign(store, { activities: [], sessions: [], breaks: new Map(), breakErrors: new Map() });
}

export async function loadStore(signal) {
  const epoch = sessionEpoch();
  const [activities, sessions] = await Promise.all([api.activities.list({ signal }), api.sessions.list({ signal })]);
  if (!Array.isArray(activities) || !Array.isArray(sessions)) throw new Error('The server returned an unexpected list response.');
  const running = sessions.filter(session => !session.endTime);
  const results = await Promise.allSettled(running.map(session => api.sessions.breaks(session.id, { signal })));
  if (signal?.aborted || epoch !== sessionEpoch()) throw new DOMException('Request cancelled', 'AbortError');
  const breaks = new Map();
  const breakErrors = new Map();
  results.forEach((result, index) => {
    if (result.status === 'fulfilled') breaks.set(running[index].id, result.value);
    else breakErrors.set(running[index].id, result.reason.message);
  });
  Object.assign(store, { activities, sessions, breaks, breakErrors });
  return store;
}
