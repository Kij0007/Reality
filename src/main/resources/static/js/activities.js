import { CATEGORIES, WEEKDAYS, label } from './domain.js';
import { escapeHtml as e, formatDate, emptyState, badge } from './ui.js';

let search = '';
let category = '';
export function reset() { search = ''; category = ''; }
export async function render(ctx) {
  ctx.root.innerHTML = `<div class="section-heading"><div><p class="eyebrow">Your commitments</p><h2>Small actions. Real progress.</h2><p class="muted">Define a daily target and a rhythm you can keep.</p></div><button class="button button-primary" data-action="create-activity">+ Create activity</button></div><div class="toolbar"><label class="field search-field"><span class="sr-only">Search activities</span><input id="activity-search" type="search" placeholder="Search activities…" value="${e(search)}"></label><label class="field"><span class="sr-only">Filter category</span><select id="category-filter"><option value="">All categories</option>${CATEGORIES.map(item => `<option value="${item}" ${category === item ? 'selected' : ''}>${label(item)}</option>`).join('')}</select></label><span class="muted" id="activity-count"></span></div><div id="activity-list"></div>`;
  const draw = () => {
    const filtered = ctx.store.activities.filter(activity => (!category || activity.category === category) && String(activity.name || '').toLowerCase().includes(search.toLowerCase()));
    ctx.root.querySelector('#activity-count').textContent = `${filtered.length} ${filtered.length === 1 ? 'activity' : 'activities'}`;
    ctx.root.querySelector('#activity-list').innerHTML = !ctx.store.activities.length ? emptyState('Your first commitment starts here', 'Create an activity, set your daily target, and start recording your work.', '<button class="button button-primary" data-action="create-activity">Create activity</button>') : !filtered.length ? emptyState('No matching activities', 'Try another name or category.') : `<div class="activity-grid">${filtered.map(activity => {
      const running = ctx.store.sessions.some(session => session.activityId === activity.id && !session.endTime);
      return `<article class="activity-card"><div class="card-top"><span class="category-icon category-${e(activity.category || 'OTHER')}" aria-hidden="true">${e((activity.name || 'R').charAt(0).toUpperCase())}</span>${badge(label(activity.category))}</div><h3>${e(activity.name || `Activity #${activity.id}`)}</h3><p class="activity-target"><strong>${e(activity.minimumDuration)}</strong> min <span class="muted">daily target</span></p><div class="week-strip" aria-label="Scheduled days">${WEEKDAYS.map(day => `<span class="${(activity.scheduledDays || []).includes(day) ? 'selected' : ''}" title="${label(day)}">${day.slice(0, 1)}</span>`).join('')}</div><p class="muted small">Since ${e(formatDate(activity.startDate))}</p><div class="card-actions"><button class="button button-primary button-small" data-action="start-session" data-id="${activity.id}" ${running ? 'disabled' : ''}>${running ? 'Session running' : 'Start session'}</button><button class="button button-secondary button-small" data-action="view-activity" data-id="${activity.id}">Details</button><button class="button button-quiet button-small" data-action="edit-activity" data-id="${activity.id}">Edit</button><button class="button button-quiet button-small danger-text" data-action="delete-activity" data-id="${activity.id}" ${running ? 'disabled title="Finish running sessions first"' : ''}>Delete</button></div></article>`;
    }).join('')}</div>`;
  };
  draw();
  ctx.root.querySelector('#activity-search').addEventListener('input', event => { search = event.target.value; draw(); }, { signal: ctx.signal });
  ctx.root.querySelector('#category-filter').addEventListener('change', event => { category = event.target.value; draw(); }, { signal: ctx.signal });
}
