import http from 'node:http';
import { URL } from 'node:url';

const PORT = process.env.PORT || 10000;
const REPO = process.env.GITHUB_REPO || 'GrapeGG-developer/gt-unblock';
const DB_URL = (process.env.TURSO_URL || '').replace(/^libsql:/, 'https:');
const DB_TOKEN = process.env.TURSO_TOKEN || '';
const ADMIN_KEY = process.env.ADMIN_KEY || '';
const GH_TOKEN = process.env.GH_TOKEN || '';

const cache = new Map();

async function cached(key, ttl, fn) {
  const hit = cache.get(key);
  const now = Date.now();
  if (hit && now - hit.t < ttl) return hit.v;
  try {
    const v = await fn();
    cache.set(key, { t: now, v });
    return v;
  } catch (e) {
    if (hit) return hit.v;
    throw e;
  }
}

function arg(v) {
  if (v === null || v === undefined) return { type: 'null' };
  if (typeof v === 'number') return Number.isInteger(v) ? { type: 'integer', value: String(v) } : { type: 'float', value: v };
  if (typeof v === 'boolean') return { type: 'integer', value: v ? '1' : '0' };
  return { type: 'text', value: String(v) };
}

async function sql(statements) {
  if (!DB_URL) throw new Error('db not configured');
  const requests = statements.map(([q, a = []]) => ({ type: 'execute', stmt: { sql: q, args: a.map(arg) } }));
  requests.push({ type: 'close' });
  const r = await fetch(DB_URL + '/v2/pipeline', {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + DB_TOKEN, 'Content-Type': 'application/json' },
    body: JSON.stringify({ requests }),
  });
  if (!r.ok) throw new Error('db http ' + r.status);
  const j = await r.json();
  return j.results.filter(x => x.type !== 'ok' || x.response.type === 'execute').map(x => {
    if (x.type !== 'ok') throw new Error(x.error?.message || 'db error');
    const res = x.response.result;
    const cols = res.cols.map(c => c.name);
    return res.rows.map(row => Object.fromEntries(row.map((cell, i) => [cols[i], cell.type === 'null' ? null : cell.value])));
  });
}

async function migrate() {
  await sql([
    [`CREATE TABLE IF NOT EXISTS reports (id INTEGER PRIMARY KEY, t INTEGER, asn TEXT, country TEXT, service TEXT, strategy TEXT, direct INTEGER, ok INTEGER, ms INTEGER, full INTEGER, v TEXT, sdk INTEGER)`],
    [`CREATE INDEX IF NOT EXISTS reports_asn ON reports(asn, t)`],
    [`CREATE TABLE IF NOT EXISTS scores (id INTEGER PRIMARY KEY, t INTEGER, asn TEXT, strategy TEXT, ok INTEGER)`],
    [`CREATE INDEX IF NOT EXISTS scores_asn ON scores(asn, t)`],
    [`CREATE TABLE IF NOT EXISTS pings (id INTEGER PRIMARY KEY, t INTEGER, v TEXT, sdk INTEGER, model TEXT, maker TEXT, abi TEXT, lang TEXT, mode TEXT, asn TEXT, country TEXT, expert INTEGER, quic INTEGER, apps TEXT)`],
    [`CREATE TABLE IF NOT EXISTS crashes (id INTEGER PRIMARY KEY, t INTEGER, v TEXT, sdk INTEGER, model TEXT, trace TEXT)`],
    [`CREATE TABLE IF NOT EXISTS feedback (id INTEGER PRIMARY KEY, t INTEGER, v TEXT, sdk INTEGER, model TEXT, text TEXT, contact TEXT)`],
  ]);
}

function send(res, code, body, headers = {}) {
  const isObj = typeof body === 'object' && !Buffer.isBuffer(body);
  res.writeHead(code, {
    'Content-Type': isObj ? 'application/json; charset=utf-8' : 'text/plain; charset=utf-8',
    'Cache-Control': 'no-store',
    ...headers,
  });
  res.end(isObj ? JSON.stringify(body) : body);
}

async function readJson(req, limit = 64 * 1024) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', c => {
      size += c.length;
      if (size > limit) {
        reject(new Error('too large'));
        req.destroy();
      } else chunks.push(c);
    });
    req.on('end', () => {
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}'));
      } catch (e) {
        reject(e);
      }
    });
    req.on('error', reject);
  });
}

const str = (v, n = 200) => (v === null || v === undefined ? null : String(v).slice(0, n));
const int = v => (Number.isFinite(Number(v)) ? Math.trunc(Number(v)) : null);

function gh(path) {
  const headers = { 'User-Agent': 'gt-unblock-api', Accept: 'application/vnd.github+json' };
  if (GH_TOKEN) headers.Authorization = 'Bearer ' + GH_TOKEN;
  return fetch('https://api.github.com/repos/' + REPO + path, { headers }).then(r => {
    if (!r.ok) throw new Error('github ' + r.status);
    return r.json();
  });
}

const ABIS = ['arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86', 'universal'];

function decodeEntities(t) {
  return t.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&amp;/g, '&');
}

async function releasesFromAtom() {
  const r = await fetch(`https://github.com/${REPO}/releases.atom`, { headers: { 'User-Agent': 'gt-unblock-api' } });
  if (!r.ok) throw new Error('atom ' + r.status);
  const xml = await r.text();
  const entries = [...xml.matchAll(/<entry>([\s\S]*?)<\/entry>/g)].map(m => m[1]);
  return entries.map(e => {
    const link = (e.match(/<link[^>]*href="([^"]+)"/) || [])[1] || '';
    const tag = decodeURIComponent(link.split('/tag/')[1] || '');
    const html = decodeEntities((e.match(/<content[^>]*>([\s\S]*?)<\/content>/) || [])[1] || '');
    const body = html.replace(/<li>/g, '- ').replace(/<\/(p|li|h\d)>/g, '\n').replace(/<br\s*\/?>/g, '\n').replace(/<[^>]+>/g, '').replace(/\n{3,}/g, '\n\n').trim();
    const version = tag.replace(/^v/, '');
    return {
      tag_name: tag,
      draft: false,
      prerelease: version.includes('-'),
      body,
      published_at: (e.match(/<updated>([^<]+)<\/updated>/) || [])[1],
      assets: ABIS.map(a => ({
        name: `GT-Unblock-${version}-${a}.apk`,
        browser_download_url: `https://github.com/${REPO}/releases/download/${tag}/GT-Unblock-${version}-${a}.apk`,
        size: 0,
      })),
    };
  }).filter(x => x.tag_name);
}

async function releases() {
  return cached('releases', 60 * 1000, async () => {
    try {
      return await gh('/releases?per_page=20');
    } catch (e) {
      return await releasesFromAtom();
    }
  });
}

async function presets(file) {
  return cached('presets:' + file, 30 * 60 * 1000, async () => {
    const r = await fetch(`https://raw.githubusercontent.com/${REPO}/main/presets/${file}`, { headers: { 'User-Agent': 'gt-unblock-api' } });
    if (!r.ok) throw new Error('presets ' + r.status);
    return r.text();
  });
}

async function handle(req, res) {
  const url = new URL(req.url, 'http://x');
  const p = url.pathname;

  if (p === '/' || p === '/health') return send(res, 200, { ok: true, service: 'gt-unblock-api' });

  if (req.method === 'GET' && (p === '/v1/presets' || p === '/v2/presets')) {
    const text = await presets(p === '/v2/presets' ? 'presets.json' : 'presets_v1.json');
    return send(res, 200, text, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'public, max-age=600' });
  }

  if (req.method === 'GET' && p === '/v1/version') {
    const channel = url.searchParams.get('channel') === 'beta' ? 'beta' : 'stable';
    const list = (await releases()).filter(r => !r.draft && (channel === 'beta' || !r.prerelease));
    const rel = list[0];
    if (!rel) return send(res, 404, { error: 'no release' });
    return send(res, 200, { v: rel.tag_name.replace(/^v/, '') }, { 'Cache-Control': 'no-store' });
  }

  if (req.method === 'GET' && p === '/v1/update') {
    const channel = url.searchParams.get('channel') === 'beta' ? 'beta' : 'stable';
    const list = (await releases()).filter(r => !r.draft && (channel === 'beta' || !r.prerelease));
    const rel = list[0];
    if (!rel) return send(res, 404, { error: 'no release' });
    const version = rel.tag_name.replace(/^v/, '');
    const assets = {};
    const sizes = {};
    for (const a of rel.assets || []) {
      if (a.name.endsWith('.apk')) {
        assets[a.name] = a.browser_download_url;
        if (a.size) sizes[a.name] = a.size;
      }
    }
    const host = req.headers['x-forwarded-host'] || req.headers.host;
    const abi = str(url.searchParams.get('abi'), 20) || '';
    const want = `GT-Unblock-${version}-${abi}.apk`;
    const pick = assets[want] ? want : `GT-Unblock-${version}-universal.apk`;
    return send(res, 200, {
      version,
      code: 0,
      changelog: rel.body || '',
      beta: !!rel.prerelease,
      published: rel.published_at,
      assets,
      sizes,
      asset: pick,
      size: sizes[pick] || 0,
      mirror: `https://${host}/v1/apk/${encodeURIComponent(rel.tag_name)}/${encodeURIComponent(pick)}`,
    });
  }

  const apk = p.match(/^\/v1\/apk\/([^/]+)\/([^/]+\.apk)$/);
  if (req.method === 'GET' && apk) {
    const tag = decodeURIComponent(apk[1]);
    const name = decodeURIComponent(apk[2]);
    if (!/^[\w.\-]+$/.test(tag) || !/^[\w.\-]+$/.test(name)) return send(res, 400, { error: 'bad name' });
    const r = await fetch(`https://github.com/${REPO}/releases/download/${tag}/${name}`, { redirect: 'follow' });
    if (!r.ok || !r.body) return send(res, 404, { error: 'not found' });
    res.writeHead(200, {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Length': r.headers.get('content-length') || undefined,
      'Content-Disposition': `attachment; filename="${name}"`,
    });
    const reader = r.body.getReader();
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (!res.write(value)) await new Promise(ok => res.once('drain', ok));
    }
    return res.end();
  }

  if (req.method === 'GET' && p === '/v1/best') {
    const asn = str(url.searchParams.get('asn'), 16);
    if (!asn || !/^\d+$/.test(asn)) return send(res, 200, { strategies: [] });
    const since = Date.now() - 30 * 24 * 3600 * 1000;
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
  }

  if (req.method === 'POST' && p === '/v1/report') {
    const b = await readJson(req);
    const d = b.device || {};
    const asn = str(b.asn, 16);
    const t = Date.now();
    const st = [];
    for (const c of (Array.isArray(b.choices) ? b.choices : []).slice(0, 40)) {
      st.push([`INSERT INTO reports (t, asn, country, service, strategy, direct, ok, ms, full, v, sdk) VALUES (?,?,?,?,?,?,?,?,?,?,?)`,
        [t, asn, str(b.country, 4), str(c.service, 32), str(c.strategy, 16), c.direct ? 1 : 0, c.ok ? 1 : 0, int(c.ms), b.full ? 1 : 0, str(d.v, 32), int(d.sdk)]]);
    }
    const scores = b.scores && typeof b.scores === 'object' ? Object.entries(b.scores).slice(0, 120) : [];
    for (const [k, v] of scores) st.push([`INSERT INTO scores (t, asn, strategy, ok) VALUES (?,?,?,?)`, [t, asn, str(k, 16), int(v)]]);
    if (st.length) await sql(st);
    return send(res, 200, { ok: true });
  }

  if (req.method === 'POST' && p === '/v1/ping') {
    const b = await readJson(req);
    const d = b.device || {};
    await sql([[`INSERT INTO pings (t, v, sdk, model, maker, abi, lang, mode, asn, country, expert, quic, apps) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)`,
      [Date.now(), str(d.v, 32), int(d.sdk), str(d.model, 64), str(d.maker, 64), str(d.abi, 20), str(b.lang, 8), str(b.mode, 8), str(b.asn, 16), str(b.country, 4), b.expert ? 1 : 0, b.quic ? 1 : 0, str(b.apps, 8)]]]);
    return send(res, 200, { ok: true });
  }

  if (req.method === 'POST' && p === '/v1/crash') {
    const b = await readJson(req, 128 * 1024);
    const d = b.device || {};
    await sql([[`INSERT INTO crashes (t, v, sdk, model, trace) VALUES (?,?,?,?,?)`, [Date.now(), str(d.v, 32), int(d.sdk), str(d.model, 64), str(b.trace, 30000)]]]);
    return send(res, 200, { ok: true });
  }

  if (req.method === 'POST' && p === '/v1/feedback') {
    const b = await readJson(req);
    const d = b.device || {};
    const text = str(b.text, 4000);
    if (!text || !text.trim()) return send(res, 400, { error: 'empty' });
    await sql([[`INSERT INTO feedback (t, v, sdk, model, text, contact) VALUES (?,?,?,?,?,?)`, [Date.now(), str(d.v, 32), int(d.sdk), str(d.model, 64), text, str(b.contact, 200)]]]);
    return send(res, 200, { ok: true });
  }

  if (req.method === 'GET' && p.startsWith('/v1/admin/')) {
    if (!ADMIN_KEY || req.headers['x-admin-key'] !== ADMIN_KEY) return send(res, 403, { error: 'forbidden' });
    const table = p.slice('/v1/admin/'.length);
    if (table === 'stats') {
      const day = Date.now() - 24 * 3600 * 1000;
      const [a, b, c, d] = await sql([
        [`SELECT COUNT(*) AS n FROM pings WHERE t > ?`, [day]],
        [`SELECT v, COUNT(*) AS n FROM pings WHERE t > ? GROUP BY v ORDER BY n DESC`, [day]],
        [`SELECT asn, COUNT(*) AS n FROM pings WHERE t > ? GROUP BY asn ORDER BY n DESC LIMIT 20`, [day]],
        [`SELECT service, SUM(ok) AS ok, COUNT(*) AS n FROM reports WHERE t > ? GROUP BY service`, [day]],
      ]);
      return send(res, 200, { pings24h: a[0]?.n, versions: b, asns: c, services: d });
    }
    if (['feedback', 'crashes'].includes(table)) {
      const [rows] = await sql([[`SELECT * FROM ${table} ORDER BY id DESC LIMIT 100`]]);
      return send(res, 200, rows);
    }
    return send(res, 404, { error: 'unknown' });
  }

  return send(res, 404, { error: 'not found' });
}

const server = http.createServer((req, res) => {
  handle(req, res).catch(e => {
    if (!res.headersSent) send(res, 500, { error: 'internal', message: String(e.message || e).slice(0, 200) });
    else res.end();
  });
});

migrate().then(() => console.log('db ready')).catch(e => console.error('migrate failed', e.message));
server.listen(PORT, '0.0.0.0', () => console.log('listening on ' + PORT));
