import crypto from 'node:crypto';
import { readFileSync } from 'node:fs';
import { sql, sqlSafe } from './db.js';
import { releaseList, dropCache, bucket, displayName } from './releases.js';
import * as live from './live.js';

const ADMIN_KEY = process.env.ADMIN_KEY || '';
const ADMIN_PASS = process.env.ADMIN_PASS || '';
const ADMIN_TOTP = process.env.ADMIN_TOTP || '';
const ADMIN_LINK = process.env.ADMIN_LINK || '';
const SECRET = crypto.createHash('sha256').update('glyde-session:' + ADMIN_KEY + ':' + ADMIN_PASS + ':' + (process.env.ADMIN_LINK || '')).digest();
const fails = new Map();
let panel = '';
try {
  panel = readFileSync(new URL('./panel.html', import.meta.url), 'utf8');
} catch (e) {
  panel = '<h1>panel missing</h1>';
}

function b32decode(s) {
  const alpha = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const ch of s.replace(/=+$/, '').toUpperCase().replace(/\s/g, '')) {
    const v = alpha.indexOf(ch);
    if (v < 0) continue;
    bits += v.toString(2).padStart(5, '0');
  }
  const out = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) out.push(parseInt(bits.slice(i, i + 8), 2));
  return Buffer.from(out);
}

function totp(secret, step) {
  const key = b32decode(secret);
  const buf = Buffer.alloc(8);
  buf.writeBigUInt64BE(BigInt(step));
  const h = crypto.createHmac('sha1', key).update(buf).digest();
  const o = h[h.length - 1] & 15;
  const code = ((h.readUInt32BE(o) & 0x7fffffff) % 1000000).toString().padStart(6, '0');
  return code;
}

function totpOk(code) {
  if (!ADMIN_TOTP) return true;
  const step = Math.floor(Date.now() / 30000);
  const c = String(code || '').replace(/\s/g, '');
  return [-1, 0, 1].some(d => safeEq(totp(ADMIN_TOTP, step + d), c));
}

function safeEq(a, b) {
  const x = Buffer.from(String(a));
  const y = Buffer.from(String(b));
  return x.length === y.length && crypto.timingSafeEqual(x, y);
}

function passOk(p) {
  if (!ADMIN_PASS) return false;
  const [kind, salt, hash] = ADMIN_PASS.split('$');
  if (kind !== 'scrypt' || !salt || !hash) return false;
  const h = crypto.scryptSync(String(p || ''), salt, 32).toString('hex');
  return safeEq(h, hash);
}

function sign(v) {
  return crypto.createHmac('sha256', SECRET).update(v).digest('base64url');
}

function sessionOk(req) {
  const m = String(req.headers.cookie || '').match(/(?:^|;\s*)gs=([^;]+)/);
  if (!m) return false;
  const [exp, sig] = decodeURIComponent(m[1]).split('.');
  return Number(exp) > Date.now() && safeEq(sign(exp), sig || '');
}

export function authed(req) {
  if (ADMIN_KEY && safeEq(req.headers['x-admin-key'] || '', ADMIN_KEY)) return 'key';
  if (sessionOk(req)) return 'panel';
  return null;
}

function json(res, code, body, headers = {}) {
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', ...headers });
  res.end(JSON.stringify(body));
}

const DAY = 24 * 3600 * 1000;

async function overview() {
  const t = Date.now();
  const [tot, a24, a7, byV, models, isps, countries, rings, daily, peaks, crashes, fb, svc, upd, sdks, nets] = await sqlSafe([
    [`SELECT COUNT(*) AS n FROM devices`],
    [`SELECT COUNT(*) AS n FROM devices WHERE last > ?`, [t - DAY]],
    [`SELECT COUNT(*) AS n FROM devices WHERE last > ?`, [t - 7 * DAY]],
    [`SELECT v, COUNT(*) AS n FROM devices WHERE last > ? GROUP BY v ORDER BY n DESC LIMIT 15`, [t - 7 * DAY]],
    [`SELECT COALESCE(maker,'') || ' ' || COALESCE(model,'') AS m, COUNT(*) AS n FROM devices WHERE last > ? GROUP BY m ORDER BY n DESC LIMIT 12`, [t - 30 * DAY]],
    [`SELECT isp, asn, COUNT(*) AS n FROM devices WHERE last > ? GROUP BY isp, asn ORDER BY n DESC LIMIT 12`, [t - 30 * DAY]],
    [`SELECT country, COUNT(*) AS n FROM devices WHERE last > ? GROUP BY country ORDER BY n DESC LIMIT 12`, [t - 30 * DAY]],
    [`SELECT ring, COUNT(*) AS n FROM devices GROUP BY ring`],
    [`SELECT day, COUNT(*) AS n FROM daily WHERE day >= ? GROUP BY day ORDER BY day`, [new Date(t - 30 * DAY).toISOString().slice(0, 10)]],
    [`SELECT day, n FROM peaks ORDER BY day DESC LIMIT 30`],
    [`SELECT COUNT(*) AS n FROM crashes WHERE t > ?`, [t - DAY]],
    [`SELECT COUNT(*) AS n FROM feedback WHERE t > ?`, [t - 7 * DAY]],
    [`SELECT service, SUM(ok) AS ok, COUNT(*) AS n FROM reports WHERE t > ? GROUP BY service ORDER BY n DESC`, [t - 7 * DAY]],
    [`SELECT result, COUNT(*) AS n FROM updates WHERE t > ? GROUP BY result`, [t - DAY]],
    [`SELECT sdk, COUNT(*) AS n FROM devices WHERE last > ? GROUP BY sdk ORDER BY sdk`, [t - 30 * DAY]],
    [`SELECT net, COUNT(*) AS n FROM devices WHERE last > ? GROUP BY net`, [t - 7 * DAY]],
  ]);
  const online = live.onlineList();
  const onlineByV = {};
  for (const c of online) onlineByV[c.v || '?'] = (onlineByV[c.v || '?'] || 0) + 1;
  const s = live.stats();
  return {
    t,
    online: online.length,
    onlineByV,
    busy: online.filter(c => c.busy).length,
    peak: live.peak(),
    devices: Number(tot[0]?.n || 0),
    active24h: Number(a24[0]?.n || 0),
    active7d: Number(a7[0]?.n || 0),
    versions: byV.map(r => ({ ...r, display: displayName(r.v || '') })),
    models,
    isps,
    countries,
    rings,
    daily,
    peaks,
    crashes24h: Number(crashes[0]?.n || 0),
    feedback7d: Number(fb[0]?.n || 0),
    services: svc,
    updates24h: upd,
    sdks,
    nets,
    siege: live.settings.siege,
    requests: s.requests,
    limited: s.limited,
    checks: s.checks,
    uptime: t - s.started,
    series: live.series(),
    memory: process.memoryUsage().rss,
  };
}

async function devices(q) {
  const where = [];
  const args = [];
  const text = q.get('q');
  if (text) {
    where.push(`(id LIKE ? OR model LIKE ? OR ip LIKE ? OR isp LIKE ? OR city LIKE ? OR note LIKE ?)`);
    for (let i = 0; i < 6; i++) args.push('%' + text + '%');
  }
  if (q.get('ring')) {
    where.push('ring = ?');
    args.push(q.get('ring'));
  }
  if (q.get('v')) {
    where.push('v = ?');
    args.push(q.get('v'));
  }
  if (q.get('banned') === '1') where.push('banned = 1');
  const lim = Math.min(500, Number(q.get('limit') || 200));
  const [rows] = await sqlSafe([[`SELECT * FROM devices ${where.length ? 'WHERE ' + where.join(' AND ') : ''} ORDER BY last DESC LIMIT ${lim}`, args]]);
  const on = new Set(live.conns.keys());
  let list = rows.map(r => ({ ...r, online: on.has(r.id), short: shortId(r.id), display: displayName(r.v || '') }));
  if (q.get('online') === '1') list = list.filter(r => r.online);
  return list;
}

export function shortId(id) {
  return 'GLY-' + crypto.createHash('sha256').update(String(id)).digest('hex').slice(0, 6).toUpperCase();
}

async function findByShort(code) {
  const c = String(code || '').trim().toUpperCase();
  if (!/^GLY-[0-9A-F]{6}$/.test(c)) return null;
  const [rows] = await sqlSafe([[`SELECT id FROM devices ORDER BY last DESC LIMIT 20000`]]);
  return rows.map(r => r.id).filter(id => shortId(id) === c);
}

async function deviceDetail(id) {
  const [d, ev, up] = await sqlSafe([
    [`SELECT * FROM devices WHERE id = ?`, [id]],
    [`SELECT * FROM events WHERE device = ? ORDER BY id DESC LIMIT 50`, [id]],
    [`SELECT * FROM updates WHERE device = ? ORDER BY id DESC LIMIT 20`, [id]],
  ]);
  if (!d[0]) return null;
  return { ...d[0], short: shortId(id), online: live.conns.has(id), bucket: bucket(id), events: ev, updates: up, display: displayName(d[0].v || '') };
}

async function deviceAction(id, b, who) {
  const a = b.action;
  if (a === 'kick') return { ok: live.kick(id, b.reason || 'admin') };
  if (a === 'ban') {
    await live.ban('device', id, Number(b.minutes || 0), b.reason || 'admin', who);
    return { ok: true };
  }
  if (a === 'unban') {
    await live.unban('device', id, who);
    return { ok: true };
  }
  if (a === 'banip') {
    const [d] = await sqlSafe([[`SELECT ip FROM devices WHERE id = ?`, [id]]]);
    if (!d[0]?.ip) return { ok: false, error: 'no ip' };
    await live.ban('ip', d[0].ip, Number(b.minutes || 0), b.reason || 'admin', who);
    return { ok: true };
  }
  if (a === 'ring') {
    const ring = ['insider', 'beta', 'stable'].includes(b.ring) ? b.ring : 'stable';
    await sql([[`UPDATE devices SET ring = ? WHERE id = ?`, [ring, id]]]);
    live.setRing(id, ring);
    live.forgetDevice(id);
    live.sendTo(id, 'ring', { ring });
    await live.broadcastUpdates(c => c.id === id);
    live.logEvent('ring', 'info', null, id, { ring, by: who });
    return { ok: true };
  }
  if (a === 'note') {
    await sql([[`UPDATE devices SET note = ? WHERE id = ?`, [String(b.note || '').slice(0, 500), id]]]);
    live.forgetDevice(id);
    return { ok: true };
  }
  if (a === 'message') {
    return { ok: live.sendTo(id, 'announce', { id: Date.now(), title: String(b.title || ''), text: String(b.text || ''), level: b.level || 'info' }) };
  }
  if (a === 'update') {
    return { ok: (await live.broadcastUpdates(c => c.id === id)) > 0 };
  }
  if (a === 'delete') {
    live.kick(id, 'deleted');
    await sql([[`DELETE FROM devices WHERE id = ?`, [id]], [`DELETE FROM daily WHERE id = ?`, [id]]]);
    live.forgetDevice(id);
    return { ok: true };
  }
  return { ok: false, error: 'unknown action' };
}

async function releaseRows() {
  const list = await releaseList();
  const [stats] = await sqlSafe([[`SELECT to_v, result, COUNT(*) AS n FROM updates GROUP BY to_v, result`]]);
  const [onV] = await sqlSafe([[`SELECT v, COUNT(*) AS n FROM devices WHERE last > ? GROUP BY v`, [Date.now() - 7 * DAY]]]);
  const on = Object.fromEntries(onV.map(r => [r.v, Number(r.n)]));
  return list.map(r => {
    const s = {};
    for (const x of stats.filter(x => x.to_v === r.version)) s[x.result] = Number(x.n);
    return { ...r, changelog: r.changelog.slice(0, 4000), assets: Object.keys(r.assets), stats: s, devices: on[r.version] || 0 };
  });
}

async function releaseAction(tag, b, who) {
  const list = await releaseList();
  const rel = list.find(r => r.tag === tag);
  if (!rel) return { ok: false, error: 'no release' };
  const ring = ['insider', 'beta', 'stable'].includes(b.ring) ? b.ring : rel.ring;
  const percent = Number.isFinite(Number(b.percent)) ? Math.max(0, Math.min(100, Math.round(Number(b.percent)))) : rel.percent;
  const paused = b.paused === undefined ? rel.paused : !!b.paused;
  const pulled = b.pulled === undefined ? rel.pulled : !!b.pulled;
  await sql([[`INSERT INTO rollout (tag, ring, percent, paused, pulled, t) VALUES (?,?,?,?,?,?) ON CONFLICT(tag) DO UPDATE SET ring=excluded.ring, percent=excluded.percent, paused=excluded.paused, pulled=excluded.pulled, t=excluded.t`, [tag, ring, percent, paused ? 1 : 0, pulled ? 1 : 0, Date.now()]]]);
  dropCache('rollout');
  live.logEvent('rollout', 'info', null, null, { tag, ring, percent, paused, pulled, by: who });
  const sent = await live.broadcastUpdates();
  return { ok: true, sent };
}

async function security() {
  const [ev, top] = await sqlSafe([
    [`SELECT * FROM events ORDER BY id DESC LIMIT 300`],
    [`SELECT ip, COUNT(*) AS n FROM events WHERE t > ? AND level != 'info' AND ip IS NOT NULL GROUP BY ip ORDER BY n DESC LIMIT 20`, [Date.now() - DAY]],
  ]);
  const perIp = {};
  for (const c of live.conns.values()) perIp[c.ip] = (perIp[c.ip] || 0) + 1;
  const busyIps = Object.entries(perIp).sort((a, b) => b[1] - a[1]).slice(0, 20).map(([ip, n]) => ({ ip, n }));
  return {
    siege: live.settings.siege,
    siegeSince: live.settings.siegeSince,
    limits: { rate: live.settings.rate, burst: live.settings.burst, siegeRate: live.settings.siegeRate, maxConnPerIp: live.settings.maxConnPerIp },
    bans: live.banList(),
    events: ev,
    suspicious: top,
    connsPerIp: busyIps,
    stats: live.stats(),
  };
}

async function setFlag(k, v) {
  await sql([[`INSERT INTO flags (k, v, t) VALUES (?,?,?) ON CONFLICT(k) DO UPDATE SET v = excluded.v, t = excluded.t`, [k, JSON.stringify(v), Date.now()]]]);
}

async function securityAction(b, who) {
  const a = b.action;
  if (a === 'siege') {
    live.settings.siege = !!b.on;
    live.settings.siegeSince = b.on ? Date.now() : 0;
    await setFlag('_siege', { on: live.settings.siege, since: live.settings.siegeSince });
    live.logEvent('siege', 'warn', null, null, { on: live.settings.siege, by: who });
    return { ok: true };
  }
  if (a === 'limits') {
    for (const k of ['rate', 'burst', 'siegeRate', 'maxConnPerIp']) if (Number.isFinite(Number(b[k])) && Number(b[k]) > 0) live.settings[k] = Number(b[k]);
    await setFlag('_limits', { rate: live.settings.rate, burst: live.settings.burst, siegeRate: live.settings.siegeRate, maxConnPerIp: live.settings.maxConnPerIp });
    live.logEvent('limits', 'info', null, null, { by: who });
    return { ok: true };
  }
  if (a === 'ban') {
    const kind = b.kind === 'device' ? 'device' : 'ip';
    if (!b.value) return { ok: false };
    await live.ban(kind, String(b.value).slice(0, 64), Number(b.minutes || 0), b.reason || 'admin', who);
    return { ok: true };
  }
  if (a === 'unban') {
    await live.unban(b.kind === 'device' ? 'device' : 'ip', String(b.value), who);
    return { ok: true };
  }
  if (a === 'kickip') return { ok: true, n: live.kickAll(c => c.ip === b.value, 'admin') };
  if (a === 'kickall') {
    live.logEvent('kick_all', 'warn', null, null, { by: who });
    return { ok: true, n: live.kickAll(null, b.reason || 'maintenance') };
  }
  if (a === 'clearEvents') {
    await sql([[`DELETE FROM events WHERE t < ?`, [Date.now() - Number(b.days || 7) * DAY]]]);
    return { ok: true };
  }
  return { ok: false, error: 'unknown action' };
}

async function announcements() {
  const [rows] = await sqlSafe([[`SELECT * FROM announcements ORDER BY id DESC LIMIT 100`]]);
  return rows;
}

async function announce(b, who) {
  if (b.action === 'off') {
    await sql([[`UPDATE announcements SET active = 0 WHERE id = ?`, [Number(b.id)]]]);
    await live.loadState();
    live.broadcast('announce_off', { id: Number(b.id) });
    return { ok: true };
  }
  const title = String(b.title || '').slice(0, 120);
  const text = String(b.text || '').slice(0, 2000);
  if (!text) return { ok: false, error: 'empty' };
  const ring = ['all', 'insider', 'beta', 'stable'].includes(b.ring) ? b.ring : 'all';
  const level = ['info', 'warn', 'success'].includes(b.level) ? b.level : 'info';
  const until = Number(b.hours) > 0 ? Date.now() + Number(b.hours) * 3600000 : 0;
  await sql([[`INSERT INTO announcements (t, title, text, level, ring, until, active) VALUES (?,?,?,?,?,?,1)`, [Date.now(), title, text, level, ring, until]]]);
  await live.loadState();
  const a = live.settings.announcements[0];
  const n = live.broadcast('announce', a, c => ring === 'all' || c.ring === ring);
  live.logEvent('announce', 'info', null, null, { title, ring, by: who, n });
  return { ok: true, n };
}

async function flagAction(b, who) {
  const k = String(b.key || '').replace(/[^\w.-]/g, '').slice(0, 64);
  if (!k || k.startsWith('_')) return { ok: false, error: 'bad key' };
  if (b.action === 'delete') {
    await sql([[`DELETE FROM flags WHERE k = ?`, [k]]]);
    delete live.settings.flags[k];
  } else {
    let v = b.value;
    if (typeof v === 'string') {
      try {
        v = JSON.parse(v);
      } catch (e) {}
    }
    await setFlag(k, v);
    live.settings.flags[k] = v;
  }
  const n = live.broadcast('flags', live.settings.flags);
  live.logEvent('flag', 'info', null, null, { k, by: who });
  return { ok: true, n };
}

async function strategies() {
  const since = Date.now() - 30 * DAY;
  const [a, b] = await sqlSafe([
    [`SELECT asn, service, strategy, SUM(ok) AS ok, COUNT(*) AS n FROM reports WHERE t > ? GROUP BY asn, service, strategy ORDER BY n DESC LIMIT 400`, [since]],
    [`SELECT d.isp, d.asn, COUNT(*) AS n FROM devices d WHERE d.last > ? GROUP BY d.isp, d.asn ORDER BY n DESC LIMIT 50`, [since]],
  ]);
  return { reports: a, isps: b };
}

export async function handleAdmin(req, res, url, readJson) {
  const p = url.pathname;
  const ip = live.clientIp(req);
  const linkPath = ADMIN_LINK ? '/admin/' + ADMIN_LINK : '';
  if (req.method === 'GET' && linkPath && (p === linkPath || p === linkPath + '/')) {
    const exp = String(Date.now() + 30 * DAY);
    live.logEvent('admin_open', 'info', ip, null, {});
    res.writeHead(200, {
      'Content-Type': 'text/html; charset=utf-8',
      'Cache-Control': 'no-store',
      'X-Frame-Options': 'DENY',
      'Referrer-Policy': 'no-referrer',
      'X-Robots-Tag': 'noindex, nofollow',
      'Set-Cookie': `gs=${encodeURIComponent(exp + '.' + sign(exp))}; Path=/admin; HttpOnly; Secure; SameSite=Strict; Max-Age=${30 * 86400}`,
      'Content-Security-Policy': "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'",
    });
    return res.end(panel);
  }
  if (!p.startsWith('/admin/api/')) {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' });
    return res.end('Not found');
  }
  const who = authed(req);
  if (!who) return json(res, 401, { error: 'unauthorized' });
  if (req.method === 'POST' && who === 'panel' && req.headers['x-requested-with'] !== 'glyde') return json(res, 403, { error: 'csrf' });

  if (p === '/admin/api/stream') {
    res.writeHead(200, { 'Content-Type': 'text/event-stream; charset=utf-8', 'Cache-Control': 'no-store', Connection: 'keep-alive', 'X-Accel-Buffering': 'no' });
    res.write(`event: online\ndata: ${JSON.stringify({ n: live.conns.size })}\n\n`);
    live.addAdminStream(res);
    const hb = setInterval(() => {
      try {
        res.write(`event: tick\ndata: ${JSON.stringify({ n: live.conns.size, s: live.stats(), t: Date.now() })}\n\n`);
      } catch (e) {}
    }, 3000);
    res.on('close', () => clearInterval(hb));
    return;
  }
  if (req.method === 'GET') {
    if (p === '/admin/api/me') return json(res, 200, { ok: true, who });
    if (p === '/admin/api/overview') return json(res, 200, await overview());
    if (p === '/admin/api/online') return json(res, 200, live.onlineList());
    if (p === '/admin/api/devices') return json(res, 200, await devices(url.searchParams));
    if (p === '/admin/api/find') return json(res, 200, { ids: (await findByShort(url.searchParams.get('code'))) || [] });
    const dm = p.match(/^\/admin\/api\/device\/([\w-]+)$/);
    if (dm) {
      const d = await deviceDetail(dm[1]);
      return d ? json(res, 200, d) : json(res, 404, { error: 'not found' });
    }
    if (p === '/admin/api/releases') return json(res, 200, await releaseRows());
    if (p === '/admin/api/security') return json(res, 200, await security());
    if (p === '/admin/api/announcements') return json(res, 200, await announcements());
    if (p === '/admin/api/flags') return json(res, 200, live.settings.flags);
    if (p === '/admin/api/strategies') return json(res, 200, await strategies());
    if (p === '/admin/api/crashes') {
      const [rows] = await sqlSafe([[`SELECT * FROM crashes ORDER BY id DESC LIMIT 200`]]);
      return json(res, 200, rows);
    }
    if (p === '/admin/api/feedback') {
      const [rows] = await sqlSafe([[`SELECT * FROM feedback ORDER BY id DESC LIMIT 200`]]);
      return json(res, 200, rows);
    }
    if (p === '/admin/api/updates') {
      const [rows] = await sqlSafe([[`SELECT * FROM updates ORDER BY id DESC LIMIT 300`]]);
      return json(res, 200, rows);
    }
    return json(res, 404, { error: 'not found' });
  }
  if (req.method === 'POST') {
    const b = await readJson(req);
    const dm = p.match(/^\/admin\/api\/device\/([\w-]+)$/);
    if (dm) return json(res, 200, await deviceAction(dm[1], b, who));
    const rm = p.match(/^\/admin\/api\/release\/([\w.\-]+)$/);
    if (rm) return json(res, 200, await releaseAction(decodeURIComponent(rm[1]), b, who));
    if (p === '/admin/api/insider') {
      const ids = await findByShort(b.code);
      if (!ids || !ids.length) return json(res, 200, { ok: false, error: 'not found' });
      for (const id of ids) await deviceAction(id, { action: 'ring', ring: b.remove ? 'stable' : 'insider' }, who);
      return json(res, 200, { ok: true, ids });
    }
    if (p === '/admin/api/security') return json(res, 200, await securityAction(b, who));
    if (p === '/admin/api/announce') return json(res, 200, await announce(b, who));
    if (p === '/admin/api/flags') return json(res, 200, await flagAction(b, who));
    if (p === '/admin/api/push-updates') return json(res, 200, { ok: true, sent: await live.broadcastUpdates() });
    if (p === '/admin/api/refresh') {
      dropCache('releases');
      dropCache('rollout');
      await live.loadState();
      return json(res, 200, { ok: true, sent: await live.broadcastUpdates() });
    }
    return json(res, 404, { error: 'not found' });
  }
  return json(res, 405, { error: 'method' });
}
