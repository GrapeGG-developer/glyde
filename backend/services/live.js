import { sql, sqlSafe } from '../lib/db.js';
import { offerFor, offerPayload, parseVersion, displayName } from './releases.js';

export const conns = new Map();
const adminStreams = new Set();
const buckets = new Map();
const geoCache = new Map();
const devCache = new Map();
const bans = { ip: new Map(), device: new Map() };
export const settings = {
  siege: false,
  siegeSince: 0,
  rate: 20,
  burst: 60,
  siegeRate: 3,
  maxConnPerIp: 8,
  flags: {},
  announcements: [],
};
let peakToday = { day: '', n: 0 };
const counters = { requests: 0, limited: 0, checks: 0, started: Date.now() };
const minute = [];

export const now = () => Date.now();
export const today = () => new Date().toISOString().slice(0, 10);
export const str = (v, n = 200) => (v === null || v === undefined || v === '' ? null : String(v).slice(0, n));
export const int = v => (v !== null && v !== undefined && v !== '' && Number.isFinite(Number(v)) ? Math.trunc(Number(v)) : null);

export function clientIp(req) {
  const f = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim();
  return (f || req.socket.remoteAddress || '').replace(/^::ffff:/, '').slice(0, 64);
}

export function stats() {
  return counters;
}

export function tickMinute() {
  minute.push({ t: now(), r: counters.requests, c: counters.checks, online: conns.size });
  while (minute.length > 180) minute.shift();
}

export function series() {
  return minute;
}

export async function logEvent(kind, level, ip, device, detail) {
  const e = { t: now(), kind, level, ip: ip || null, device: device || null, detail: typeof detail === 'string' ? detail : JSON.stringify(detail || {}) };
  pushAdmin('event', e);
  sqlSafe([[`INSERT INTO events (t, kind, level, ip, device, detail) VALUES (?,?,?,?,?,?)`, [e.t, kind, level, e.ip, e.device, str(e.detail, 2000)]]]);
}

export async function loadState() {
  const [b, f, a] = await sqlSafe([
    [`SELECT * FROM bans WHERE until = 0 OR until > ?`, [now()]],
    [`SELECT * FROM flags`],
    [`SELECT * FROM announcements WHERE active = 1 AND (until = 0 OR until IS NULL OR until > ?) ORDER BY id DESC`, [now()]],
  ]);
  bans.ip.clear();
  bans.device.clear();
  for (const r of b) bans[r.kind === 'ip' ? 'ip' : 'device'].set(r.value, { until: Number(r.until || 0), reason: r.reason, t: Number(r.t || 0) });
  settings.flags = {};
  for (const r of f) {
    let v = r.v;
    try {
      v = JSON.parse(r.v);
    } catch (e) {}
    if (r.k.startsWith('_')) {
      if (r.k === '_siege') {
        settings.siege = !!v?.on;
        settings.siegeSince = v?.since || 0;
      }
      if (r.k === '_limits' && v) {
        for (const k of ['rate', 'burst', 'siegeRate', 'maxConnPerIp']) if (Number.isFinite(v[k])) settings[k] = v[k];
      }
    } else settings.flags[r.k] = v;
  }
  settings.announcements = a.map(r => ({ id: Number(r.id), title: r.title, text: r.text, level: r.level, ring: r.ring || 'all', until: Number(r.until || 0) }));
  const [pk] = await sqlSafe([[`SELECT n FROM peaks WHERE day = ?`, [today()]]]);
  peakToday = { day: today(), n: Number(pk[0]?.n || 0) };
}

export function banList() {
  const out = [];
  for (const kind of ['ip', 'device']) for (const [value, b] of bans[kind]) out.push({ kind, value, ...b });
  return out;
}

export function isBanned(ip, device) {
  const t = now();
  const a = bans.ip.get(ip);
  if (a && (!a.until || a.until > t)) return a;
  if (device) {
    const b = bans.device.get(device);
    if (b && (!b.until || b.until > t)) return b;
  }
  return null;
}

export async function ban(kind, value, minutes, reason, by) {
  const until = minutes > 0 ? now() + minutes * 60000 : 0;
  bans[kind].set(value, { until, reason, t: now() });
  await sql([[`INSERT INTO bans (kind, value, until, reason, t) VALUES (?,?,?,?,?) ON CONFLICT(kind, value) DO UPDATE SET until = excluded.until, reason = excluded.reason, t = excluded.t`, [kind, value, until, reason || '', now()]]]);
  if (kind === 'device') await sqlSafe([[`UPDATE devices SET banned = 1 WHERE id = ?`, [value]]]);
  for (const c of conns.values()) if ((kind === 'ip' && c.ip === value) || (kind === 'device' && c.id === value)) kick(c.id, 'banned');
  logEvent('ban', 'warn', kind === 'ip' ? value : null, kind === 'device' ? value : null, { by, reason, minutes });
}

export async function unban(kind, value, by) {
  bans[kind].delete(value);
  await sql([[`DELETE FROM bans WHERE kind = ? AND value = ?`, [kind, value]]]);
  if (kind === 'device') await sqlSafe([[`UPDATE devices SET banned = 0 WHERE id = ?`, [value]]]);
  logEvent('unban', 'info', kind === 'ip' ? value : null, kind === 'device' ? value : null, { by });
}

export function limit(ip, cost = 1) {
  counters.requests++;
  const rate = settings.siege ? settings.siegeRate : settings.rate;
  const burst = settings.siege ? settings.siegeRate * 4 : settings.burst;
  const t = now();
  let b = buckets.get(ip);
  if (!b) {
    b = { tokens: burst, t, hits: 0 };
    buckets.set(ip, b);
  }
  b.tokens = Math.min(burst, b.tokens + ((t - b.t) / 1000) * rate);
  b.t = t;
  if (b.tokens < cost) {
    b.hits++;
    counters.limited++;
    if (b.hits === 50 || b.hits % 1000 === 0) logEvent('rate_limit', 'warn', ip, null, { hits: b.hits });
    return false;
  }
  b.tokens -= cost;
  return true;
}

export function sweep() {
  const t = now();
  for (const [ip, b] of buckets) if (t - b.t > 10 * 60000) buckets.delete(ip);
  for (const [ip, g] of geoCache) if (t - g.t > 24 * 3600000) geoCache.delete(ip);
  for (const [id, d] of devCache) if (t - d.seen > 30 * 60000 && !conns.has(id)) devCache.delete(id);
}

export async function geo(ip) {
  if (!ip || /^(10\.|127\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|::1|fc|fd)/.test(ip)) return {};
  const hit = geoCache.get(ip);
  if (hit) return hit.v;
  const entry = { t: now(), v: {} };
  geoCache.set(ip, entry);
  try {
    const r = await fetch(`http://ip-api.com/json/${encodeURIComponent(ip)}?fields=status,countryCode,city,isp,as,mobile`, { signal: AbortSignal.timeout(4000) });
    const j = await r.json();
    if (j.status === 'success') {
      entry.v = { country: j.countryCode, city: j.city, isp: j.isp, asn: String(j.as || '').match(/^AS(\d+)/)?.[1] || null, mobile: !!j.mobile };
    }
  } catch (e) {}
  return entry.v;
}

export function deviceInfo(req, q) {
  const h = k => req.headers['x-glyde-' + k];
  const g = k => q?.get?.(k) ?? h(k);
  const id = str(g('id'), 40);
  return {
    id: id && /^[A-Za-z0-9_-]{6,40}$/.test(id) ? id : null,
    v: str(g('v'), 40),
    code: int(g('code')),
    sdk: int(g('sdk')),
    model: str(g('model'), 64),
    maker: str(g('maker'), 64),
    abi: str(g('abi'), 20) || 'universal',
    lang: str(g('lang'), 8),
    ram: int(g('ram')),
    lite: g('lite') === '1' ? 1 : 0,
    beta: g('beta') === '1' ? 1 : 0,
    net: str(g('net'), 12),
    busy: g('busy') === '1' ? 1 : 0,
  };
}

export async function getDevice(id) {
  if (!id) return null;
  const c = devCache.get(id);
  if (c) {
    c.seen = now();
    return c.row;
  }
  const [rows] = await sqlSafe([[`SELECT * FROM devices WHERE id = ?`, [id]]]);
  const row = rows[0] || null;
  devCache.set(id, { row, seen: now() });
  return row;
}

export function forgetDevice(id) {
  devCache.delete(id);
}

export async function touchDevice(info, ip, extra = {}) {
  if (!info.id) return null;
  const g = await geo(ip);
  const t = now();
  const prev = await getDevice(info.id);
  const ring = prev?.ring === 'insider' ? 'insider' : info.beta ? 'beta' : prev?.ring === 'beta' && !info.beta ? 'stable' : prev?.ring || 'stable';
  const row = {
    ...(prev || {}),
    id: info.id,
    first: prev?.first || t,
    last: t,
    v: info.v ?? prev?.v,
    code: info.code ?? prev?.code,
    sdk: info.sdk ?? prev?.sdk,
    model: info.model ?? prev?.model,
    maker: info.maker ?? prev?.maker,
    abi: info.abi ?? prev?.abi,
    lang: info.lang ?? prev?.lang,
    ram: info.ram ?? prev?.ram,
    lite: info.lite,
    ip,
    country: g.country ?? prev?.country,
    city: g.city ?? prev?.city,
    isp: g.isp ?? prev?.isp,
    asn: g.asn ?? prev?.asn,
    net: info.net ?? prev?.net,
    ring,
    beta: info.beta,
    busy: info.busy,
    ...extra,
  };
  devCache.set(info.id, { row, seen: t });
  await sqlSafe([
    [`INSERT INTO devices (id, first, last, v, code, sdk, model, maker, abi, lang, ram, lite, ip, country, city, isp, asn, net, ring, beta, busy, state, strategy, services, sessions) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1)
      ON CONFLICT(id) DO UPDATE SET last=excluded.last, v=excluded.v, code=excluded.code, sdk=excluded.sdk, model=excluded.model, maker=excluded.maker, abi=excluded.abi, lang=excluded.lang, ram=excluded.ram, lite=excluded.lite, ip=excluded.ip, country=excluded.country, city=excluded.city, isp=excluded.isp, asn=excluded.asn, net=excluded.net, ring=excluded.ring, beta=excluded.beta, busy=excluded.busy, state=COALESCE(excluded.state, devices.state), strategy=COALESCE(excluded.strategy, devices.strategy), services=COALESCE(excluded.services, devices.services), sessions=devices.sessions + ?`,
      [row.id, row.first, row.last, row.v, row.code, row.sdk, row.model, row.maker, row.abi, row.lang, row.ram, row.lite, ip, row.country, row.city, row.isp, row.asn, row.net, row.ring, row.beta, row.busy, extra.state ?? null, extra.strategy ?? null, extra.services ?? null, extra.session ? 1 : 0]],
    [`INSERT OR IGNORE INTO daily (day, id, v) VALUES (?,?,?)`, [today(), row.id, row.v]],
  ]);
  if (!prev) logEvent('new_device', 'info', ip, info.id, { model: row.model, v: row.v, country: row.country });
  return row;
}

function sseWrite(res, event, data) {
  try {
    res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
    return true;
  } catch (e) {
    return false;
  }
}

export function pushAdmin(event, data) {
  for (const s of adminStreams) sseWrite(s, event, data);
}

export function addAdminStream(res) {
  adminStreams.add(res);
  res.on('close', () => adminStreams.delete(res));
}

function annFor(ring) {
  return settings.announcements.filter(a => a.ring === 'all' || a.ring === ring);
}

export async function openLive(req, res, info, ip, host) {
  let perIp = 0;
  for (const c of conns.values()) if (c.ip === ip) perIp++;
  if (perIp >= settings.maxConnPerIp) {
    logEvent('conn_limit', 'warn', ip, info.id, { perIp });
    res.writeHead(429, { 'Content-Type': 'application/json' });
    return res.end('{"error":"too many connections"}');
  }
  const prev = await getDevice(info.id);
  if (settings.siege && !prev) {
    res.writeHead(503, { 'Content-Type': 'application/json', 'Retry-After': '300' });
    return res.end('{"error":"siege"}');
  }
  const old = conns.get(info.id);
  if (old) {
    old.replaced = true;
    try {
      old.res.end();
    } catch (e) {}
  }
  const dev = await touchDevice(info, ip, { session: true });
  res.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-store',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no',
  });
  res.socket?.setKeepAlive?.(true, 20000);
  res.socket?.setNoDelay?.(true);
  const c = { id: info.id, ip, res, since: now(), info, ring: dev?.ring || 'stable', host, lastOffer: null, busy: info.busy };
  conns.set(info.id, c);
  const offer = await safeOffer(c);
  sseWrite(res, 'hello', {
    server: now(),
    ring: c.ring,
    flags: settings.flags,
    announcements: annFor(c.ring),
    update: offer,
    siege: settings.siege,
  });
  notePeak();
  pushAdmin('online', { n: conns.size, id: info.id, on: true, v: info.v, ip, model: dev?.model, country: dev?.country });
  const hb = setInterval(() => {
    try {
      res.write(`: ${now()}\n\n`);
    } catch (e) {}
  }, 20000);
  res.on('close', () => {
    clearInterval(hb);
    if (conns.get(info.id) === c) conns.delete(info.id);
    pushAdmin('online', { n: conns.size, id: info.id, on: false });
  });
}

async function safeOffer(c) {
  try {
    const rel = await offerFor({ id: c.id, code: c.info.code, ring: c.ring });
    if (!rel) return null;
    c.lastOffer = rel.version;
    return offerPayload(rel, c.info.abi, c.host);
  } catch (e) {
    return null;
  }
}

function notePeak() {
  const d = today();
  if (peakToday.day !== d) peakToday = { day: d, n: 0 };
  if (conns.size > peakToday.n) {
    peakToday.n = conns.size;
    sqlSafe([[`INSERT INTO peaks (day, n) VALUES (?,?) ON CONFLICT(day) DO UPDATE SET n = MAX(n, excluded.n)`, [d, peakToday.n]]]);
  }
}

export function peak() {
  return peakToday;
}

export function kick(id, reason) {
  const c = conns.get(id);
  if (!c) return false;
  sseWrite(c.res, 'kick', { reason });
  try {
    c.res.end();
  } catch (e) {}
  conns.delete(id);
  return true;
}

export function kickAll(filter, reason) {
  let n = 0;
  for (const c of [...conns.values()]) if (!filter || filter(c)) n += kick(c.id, reason) ? 1 : 0;
  return n;
}

export function setRing(id, ring) {
  const c = conns.get(id);
  if (c) c.ring = ring;
  const d = devCache.get(id);
  if (d?.row) d.row.ring = ring;
}

export function setBusy(id, busy) {
  const c = conns.get(id);
  if (c) c.busy = busy;
}

export async function broadcastUpdates(only) {
  let sent = 0;
  for (const c of [...conns.values()]) {
    if (only && !only(c)) continue;
    const offer = await safeOffer(c);
    if (offer) {
      sseWrite(c.res, 'update', offer);
      sent++;
    }
  }
  pushAdmin('broadcast', { sent });
  return sent;
}

export function broadcast(event, data, filter) {
  let n = 0;
  for (const c of conns.values()) if (!filter || filter(c)) n += sseWrite(c.res, event, data) ? 1 : 0;
  return n;
}

export function sendTo(id, event, data) {
  const c = conns.get(id);
  return c ? sseWrite(c.res, event, data) : false;
}

const checkTouch = new Map();

export async function quickCheck(info, host, ip) {
  counters.checks++;
  if (info.id && ip && now() - (checkTouch.get(info.id) || 0) > 300000) {
    checkTouch.set(info.id, now());
    if (checkTouch.size > 50000) checkTouch.clear();
    touchDevice(info, ip).catch(() => {});
  }
  const dev = info.id ? await getDevice(info.id) : null;
  const ring = dev?.ring === 'insider' ? 'insider' : info.beta ? 'beta' : 'stable';
  const rel = await offerFor({ id: info.id, code: info.code, ring });
  return {
    t: now(),
    v: rel?.version || null,
    update: rel ? offerPayload(rel, info.abi, host) : null,
    flags: settings.flags,
    announcements: annFor(ring),
  };
}

export function onlineList() {
  return [...conns.values()].map(c => ({
    id: c.id,
    ip: c.ip,
    since: c.since,
    v: c.info.v,
    display: displayName(c.info.v || ''),
    ring: c.ring,
    model: c.info.model,
    sdk: c.info.sdk,
    net: c.info.net,
    busy: c.busy,
    lite: c.info.lite,
  }));
}

export function versionLabel(v) {
  const p = parseVersion(v);
  return p ? displayName(v) : v;
}
