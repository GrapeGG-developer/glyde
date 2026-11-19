import http from 'node:http';
import { URL } from 'node:url';
import { sql, sqlSafe, migrate } from './db.js';
import { REPO, cached, releaseList, allowed, offerPayload, githubReleases } from './releases.js';
import * as live from './live.js';
import { handleAdmin } from './admin.js';

const PORT = process.env.PORT || 10000;
const { str, int } = live;

function send(res, code, body, headers = {}) {
  const isObj = typeof body === 'object' && !Buffer.isBuffer(body);
  res.writeHead(code, {
    'Content-Type': isObj ? 'application/json; charset=utf-8' : 'text/plain; charset=utf-8',
    'Cache-Control': 'no-store',
    'Access-Control-Allow-Origin': '*',
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

async function presets(file) {
  return cached('presets:' + file, 10 * 60 * 1000, async () => {
    const r = await fetch(`https://raw.githubusercontent.com/${REPO}/main/presets/${file}`, { headers: { 'User-Agent': 'glyde-api' } });
    if (!r.ok) throw new Error('presets ' + r.status);
    return r.text();
  });
}

async function legacyUpdate(url, host) {
  const channel = url.searchParams.get('channel') === 'beta' ? 'beta' : 'stable';
  const list = await releaseList();
  const rel = list.find(r => allowed(r, { ring: channel }));
  if (!rel) return null;
  const abi = str(url.searchParams.get('abi'), 20) || 'universal';
  const o = offerPayload(rel, abi, host);
  const assets = { ...o.assets };
  const sizes = { ...o.sizes };
  for (const [name, link] of Object.entries(o.assets)) {
    const m = name.match(/^Glyde-(.+)-(arm64-v8a|armeabi-v7a|x86_64|x86|universal)\.apk$/);
    if (m) {
      const alias = `GT-Unblock-${m[1]}-${m[2]}.apk`;
      assets[alias] = link;
      if (sizes[name]) sizes[alias] = sizes[name];
    }
  }
  return {
    version: o.version,
    code: o.code,
    changelog: o.changelog,
    beta: rel.ring !== 'stable',
    published: o.published,
    assets,
    sizes,
    asset: o.asset,
    size: o.size,
    mirror: o.mirror,
  };
}

async function buildHashes(tag) {
  return cached('sums:' + tag, 6 * 3600 * 1000, async () => {
    const r = await fetch(`https://github.com/${REPO}/releases/download/${tag}/SHA256SUMS.txt`, { redirect: 'follow' });
    if (!r.ok) throw new Error('sums ' + r.status);
    const out = {};
    for (const line of (await r.text()).split('\n')) {
      const m = line.trim().match(/^([0-9a-f]{64})\s+\*?(.+)$/);
      if (m) out[m[2]] = m[1];
    }
    return out;
  });
}

async function streamApk(req, res, tag, name) {
  const headers = { 'User-Agent': 'glyde-api' };
  if (req.headers.range) headers.Range = req.headers.range;
  const r = await fetch(`https://github.com/${REPO}/releases/download/${tag}/${name}`, { redirect: 'follow', headers });
  if ((!r.ok && r.status !== 206) || !r.body) return send(res, 404, { error: 'not found' });
  const h = {
    'Content-Type': 'application/vnd.android.package-archive',
    'Content-Disposition': `attachment; filename="${name}"`,
    'Accept-Ranges': 'bytes',
  };
  if (r.headers.get('content-length')) h['Content-Length'] = r.headers.get('content-length');
  if (r.headers.get('content-range')) h['Content-Range'] = r.headers.get('content-range');
  res.writeHead(r.status, h);
  const reader = r.body.getReader();
  let closed = false;
  res.on('close', () => {
    closed = true;
    reader.cancel().catch(() => {});
  });
  while (!closed) {
    const { done, value } = await reader.read();
    if (done) break;
    if (!res.write(value)) await new Promise(ok => res.once('drain', ok));
  }
  res.end();
}

async function handle(req, res) {
  const url = new URL(req.url, 'http://x');
  const p = url.pathname;
  const ip = live.clientIp(req);
  const host = req.headers['x-forwarded-host'] || req.headers.host;

  if (p === '/' || p === '/health') return send(res, 200, { ok: true, service: 'glyde-api', online: live.conns.size });

  if (p === '/admin' || p.startsWith('/admin/')) return handleAdmin(req, res, url, readJson);

  if (req.method === 'OPTIONS') return send(res, 204, '', { 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': 'GET, POST' });

  const info = live.deviceInfo(req, url.searchParams);
  const banned = live.isBanned(ip, info.id);
  if (banned) return send(res, 403, { error: 'banned', reason: banned.reason || '' });
  if (!live.limit(ip, p === '/v3/check' ? 0.25 : 1)) return send(res, 429, { error: 'slow down' }, { 'Retry-After': '2' });

  if (req.method === 'GET' && p === '/v3/live') {
    if (!info.id) return send(res, 400, { error: 'id required' });
    return live.openLive(req, res, info, ip, host);
  }

  if (req.method === 'GET' && p === '/v3/check') {
    return send(res, 200, await live.quickCheck(info, host, ip));
  }

  if (req.method === 'POST' && p === '/v3/status') {
    const b = await readJson(req);
    if (!info.id) return send(res, 400, { error: 'id required' });
    const services = b.services && typeof b.services === 'object' ? JSON.stringify(b.services).slice(0, 4000) : null;
    await live.touchDevice({ ...info, busy: b.busy ? 1 : 0, net: str(b.net, 12) || info.net }, ip, {
      state: str(b.state, 24),
      strategy: str(b.strategy, 400),
      services,
    });
    live.setBusy(info.id, b.busy ? 1 : 0);
    live.pushAdmin('status', { id: info.id, state: b.state, busy: !!b.busy });
    return send(res, 200, { ok: true });
  }

  if (req.method === 'POST' && p === '/v3/update-result') {
    const b = await readJson(req);
    await sqlSafe([[`INSERT INTO updates (t, device, from_v, to_v, result, detail) VALUES (?,?,?,?,?,?)`, [Date.now(), info.id, str(b.from, 40), str(b.to, 40), str(b.result, 20), str(b.detail, 500)]]]);
    live.pushAdmin('update_result', { id: info.id, ...b });
    if (b.result === 'failed') live.logEvent('update_failed', 'warn', ip, info.id, { to: b.to, detail: str(b.detail, 300) });
    return send(res, 200, { ok: true });
  }

  if (req.method === 'GET' && p === '/v3/build') {
    const tag = str(url.searchParams.get('tag'), 40);
    if (!tag || !/^v[\w.\-]+$/.test(tag)) return send(res, 400, { error: 'bad tag' });
    try {
      return send(res, 200, { tag, sums: await buildHashes(tag), actions: `https://github.com/${REPO}/actions`, release: `https://github.com/${REPO}/releases/tag/${tag}` });
    } catch (e) {
      return send(res, 404, { error: 'no sums' });
    }
  }

  if (req.method === 'GET' && (p === '/v1/presets' || p === '/v2/presets' || p === '/v3/presets')) {
    const file = p === '/v3/presets' ? 'presets_v3.json' : p === '/v2/presets' ? 'presets.json' : 'presets_v1.json';
    const text = await presets(file);
    return send(res, 200, text, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'public, max-age=600' });
  }

  if (req.method === 'GET' && p === '/v1/version') {
    const o = await legacyUpdate(url, host);
    if (!o) return send(res, 404, { error: 'no release' });
    return send(res, 200, { v: o.version });
  }

  if (req.method === 'GET' && p === '/v1/update') {
    const o = await legacyUpdate(url, host);
    if (!o) return send(res, 404, { error: 'no release' });
    return send(res, 200, o);
  }

  const apk = p.match(/^\/v1\/apk\/([^/]+)\/([^/]+\.apk)$/);
  if (req.method === 'GET' && apk) {
    const tag = decodeURIComponent(apk[1]);
    const name = decodeURIComponent(apk[2]);
    if (!/^[\w.\-]+$/.test(tag) || !/^[\w.\-]+$/.test(name)) return send(res, 400, { error: 'bad name' });
    return streamApk(req, res, tag, name);
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

  if (req.method === 'GET' && p === '/v3/services') {
    const asn = str(url.searchParams.get('asn'), 16);
    if (!asn || !/^\d+$/.test(asn)) return send(res, 200, { services: {} });
    const rows = await cached('svc:' + asn, 5 * 60 * 1000, async () => {
      const [a] = await sql([[`SELECT service, SUM(ok) AS ok, COUNT(*) AS n FROM reports WHERE asn = ? AND t > ? GROUP BY service`, [asn, Date.now() - 3 * 24 * 3600 * 1000]]]);
      return Object.fromEntries(a.map(r => [r.service, { ok: Number(r.ok), n: Number(r.n) }]));
    });
    return send(res, 200, { asn, services: rows });
  }

  if (req.method === 'POST' && p === '/v1/report') {
    const b = await readJson(req);
    const d = b.device || {};
    const asn = str(b.asn, 16);
    const t = Date.now();
    const st = [];
    for (const c of (Array.isArray(b.choices) ? b.choices : []).slice(0, 60)) {
      st.push([`INSERT INTO reports (t, asn, country, service, strategy, direct, ok, ms, full, v, sdk) VALUES (?,?,?,?,?,?,?,?,?,?,?)`,
        [t, asn, str(b.country, 4), str(c.service, 32), str(c.strategy, 16), c.direct ? 1 : 0, c.ok ? 1 : 0, int(c.ms), b.full ? 1 : 0, str(d.v, 40), int(d.sdk)]]);
    }
    const scores = b.scores && typeof b.scores === 'object' ? Object.entries(b.scores).slice(0, 200) : [];
    for (const [k, v] of scores) st.push([`INSERT INTO scores (t, asn, strategy, ok) VALUES (?,?,?,?)`, [t, asn, str(k, 16), int(v)]]);
    if (st.length) await sql(st);
    return send(res, 200, { ok: true });
  }

  if (req.method === 'POST' && p === '/v1/ping') {
    const b = await readJson(req);
    const d = b.device || {};
    await sql([[`INSERT INTO pings (t, v, sdk, model, maker, abi, lang, mode, asn, country, expert, quic, apps) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)`,
      [Date.now(), str(d.v, 40), int(d.sdk), str(d.model, 64), str(d.maker, 64), str(d.abi, 20), str(b.lang, 8), str(b.mode, 8), str(b.asn, 16), str(b.country, 4), b.expert ? 1 : 0, b.quic ? 1 : 0, str(b.apps, 8)]]]);
    if (info.id) await live.touchDevice(info, ip);
    return send(res, 200, { ok: true });
  }

  if (req.method === 'POST' && p === '/v1/crash') {
    const b = await readJson(req, 128 * 1024);
    const d = b.device || {};
    await sql([[`INSERT INTO crashes (t, v, sdk, model, trace) VALUES (?,?,?,?,?)`, [Date.now(), str(d.v, 40), int(d.sdk), str(d.model, 64), str(b.trace, 30000)]]]);
    live.pushAdmin('crash', { v: d.v, model: d.model });
    return send(res, 200, { ok: true });
  }

  if (req.method === 'POST' && p === '/v1/feedback') {
    const b = await readJson(req);
    const d = b.device || {};
    const text = str(b.text, 4000);
    if (!text || !text.trim()) return send(res, 400, { error: 'empty' });
    await sql([[`INSERT INTO feedback (t, v, sdk, model, text, contact) VALUES (?,?,?,?,?,?)`, [Date.now(), str(d.v, 40), int(d.sdk), str(d.model, 64), text, str(b.contact, 200)]]]);
    live.pushAdmin('feedback', { v: d.v });
    return send(res, 200, { ok: true });
  }

  if (req.method === 'GET' && p.startsWith('/v1/admin/')) {
    return send(res, 410, { error: 'use /admin/api' });
  }

  return send(res, 404, { error: 'not found' });
}

const server = http.createServer((req, res) => {
  handle(req, res).catch(e => {
    if (!res.headersSent) send(res, 500, { error: 'internal', message: String(e.message || e).slice(0, 200) });
    else res.end();
  });
});
server.keepAliveTimeout = 65000;
server.headersTimeout = 70000;
server.requestTimeout = 0;

async function retention() {
  const t = Date.now();
  await sqlSafe([
    [`UPDATE devices SET ip = NULL WHERE last < ? AND ip IS NOT NULL`, [t - 30 * 24 * 3600 * 1000]],
    [`DELETE FROM events WHERE t < ?`, [t - 30 * 24 * 3600 * 1000]],
    [`DELETE FROM pings WHERE t < ?`, [t - 180 * 24 * 3600 * 1000]],
    [`DELETE FROM daily WHERE day < ?`, [new Date(t - 400 * 24 * 3600 * 1000).toISOString().slice(0, 10)]],
  ]);
}

let known = null;
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

migrate()
  .then(() => live.loadState())
  .then(() => console.log('db ready'))
  .catch(e => console.error('init failed', e.message));
setInterval(() => live.sweep(), 60000);
setInterval(() => live.tickMinute(), 60000);
setInterval(() => retention(), 3600000);
setInterval(() => watchReleases(), 30000);
setInterval(() => live.loadState().catch(() => {}), 5 * 60000);
server.listen(PORT, '0.0.0.0', () => console.log('listening on ' + PORT));
