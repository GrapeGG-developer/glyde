import { sqlSafe } from '../lib/db.js';
import { githubReleases } from './releases.js';
import * as live from './live.js';

const DAY = 24 * 3600 * 1000;
let known = null;

async function retention() {
  const t = Date.now();
  await sqlSafe([
    [`UPDATE devices SET ip = NULL WHERE last < ? AND ip IS NOT NULL`, [t - 30 * DAY]],
    [`DELETE FROM events WHERE t < ?`, [t - 30 * DAY]],
    [`DELETE FROM pings WHERE t < ?`, [t - 180 * DAY]],
    [`DELETE FROM daily WHERE day < ?`, [new Date(t - 400 * DAY).toISOString().slice(0, 10)]],
  ]);
}

async function watchReleases() {
  try {
    const list = await githubReleases();
    const tags = list.map(r => r.tag_name).join(',');
    if (known !== null && tags !== known) {
      const sent = await live.broadcastUpdates();
      live.logEvent('release_seen', 'info', null, null, { tags: list.slice(0, 3).map(r => r.tag_name), sent });
    }
    known = tags;
  } catch (e) {}
}

export function startJobs() {
  setInterval(() => live.sweep(), 60000);
  setInterval(() => live.tickMinute(), 60000);
  setInterval(() => retention(), 3600000);
  setInterval(() => watchReleases(), 30000);
  setInterval(() => live.loadState().catch(() => {}), 5 * 60000);
}
