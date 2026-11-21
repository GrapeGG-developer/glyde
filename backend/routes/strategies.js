import { send } from '../lib/http.js';
import { sql } from '../lib/db.js';
import { REPO, cached } from '../services/releases.js';
import { str } from '../services/live.js';

const FILES = { '/v1/presets': 'presets_v1.json', '/v2/presets': 'presets.json', '/v3/presets': 'presets_v3.json' };
const DAY = 24 * 3600 * 1000;

function presets(file) {
  return cached('presets:' + file, 10 * 60 * 1000, async () => {
    const r = await fetch(`https://raw.githubusercontent.com/${REPO}/main/presets/${file}`, { headers: { 'User-Agent': 'glyde-api' } });
    if (!r.ok) throw new Error('presets ' + r.status);
    return r.text();
  });
}

async function servePresets({ res, path }) {
  const text = await presets(FILES[path]);
  return send(res, 200, text, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'public, max-age=600' });
}

function asnOf(url) {
  const asn = str(url.searchParams.get('asn'), 16);
  return asn && /^\d+$/.test(asn) ? asn : null;
}

export default {
  'GET /v1/presets': servePresets,
  'GET /v2/presets': servePresets,
  'GET /v3/presets': servePresets,

  'GET /v1/best': async ({ res, url }) => {
    const asn = asnOf(url);
    if (!asn) return send(res, 200, { strategies: [] });
    const since = Date.now() - 30 * DAY;
    const rows = await cached('best:' + asn, 5 * 60 * 1000, async () => {
      const [a, b] = await sql([
        [`SELECT strategy, SUM(ok) AS good, COUNT(*) AS n FROM reports WHERE asn = ? AND t > ? AND strategy IS NOT NULL AND direct = 0 GROUP BY strategy`, [asn, since]],
        [`SELECT strategy, SUM(ok) AS good, COUNT(*) AS n FROM scores WHERE asn = ? AND t > ? GROUP BY strategy`, [asn, since]],
      ]);
      const score = new Map();
      for (const r of a) score.set(r.strategy, (score.get(r.strategy) || 0) + Number(r.good) * 3 + Number(r.n));
      for (const r of b) score.set(r.strategy, (score.get(r.strategy) || 0) + Number(r.good));
      return [...score.entries()].sort((x, y) => y[1] - x[1]).slice(0, 24).map(x => x[0]);
    });
    return send(res, 200, { asn, strategies: rows });
  },

  'GET /v3/services': async ({ res, url }) => {
    const asn = asnOf(url);
    if (!asn) return send(res, 200, { services: {} });
    const rows = await cached('svc:' + asn, 5 * 60 * 1000, async () => {
      const [a] = await sql([[`SELECT service, SUM(ok) AS ok, COUNT(*) AS n FROM reports WHERE asn = ? AND t > ? GROUP BY service`, [asn, Date.now() - 3 * DAY]]]);
      return Object.fromEntries(a.map(r => [r.service, { ok: Number(r.ok), n: Number(r.n) }]));
    });
    return send(res, 200, { asn, services: rows });
  },
};
