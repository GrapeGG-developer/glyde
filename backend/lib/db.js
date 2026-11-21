const DB_URL = (process.env.TURSO_URL || '').replace(/^libsql:/, 'https:');
const DB_TOKEN = process.env.TURSO_TOKEN || '';

function arg(v) {
  if (v === null || v === undefined) return { type: 'null' };
  if (typeof v === 'number') return Number.isInteger(v) ? { type: 'integer', value: String(v) } : { type: 'float', value: v };
  if (typeof v === 'boolean') return { type: 'integer', value: v ? '1' : '0' };
  return { type: 'text', value: String(v) };
}

export const dbReady = () => !!DB_URL;

export async function sql(statements) {
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

export async function sqlSafe(statements) {
  try {
    return await sql(statements);
  } catch (e) {
    console.error('sql', e.message);
    return statements.map(() => []);
  }
}

export async function migrate() {
  await sql([
    [`CREATE TABLE IF NOT EXISTS reports (id INTEGER PRIMARY KEY, t INTEGER, asn TEXT, country TEXT, service TEXT, strategy TEXT, direct INTEGER, ok INTEGER, ms INTEGER, full INTEGER, v TEXT, sdk INTEGER)`],
    [`CREATE INDEX IF NOT EXISTS reports_asn ON reports(asn, t)`],
    [`CREATE TABLE IF NOT EXISTS scores (id INTEGER PRIMARY KEY, t INTEGER, asn TEXT, strategy TEXT, ok INTEGER)`],
    [`CREATE INDEX IF NOT EXISTS scores_asn ON scores(asn, t)`],
    [`CREATE TABLE IF NOT EXISTS pings (id INTEGER PRIMARY KEY, t INTEGER, v TEXT, sdk INTEGER, model TEXT, maker TEXT, abi TEXT, lang TEXT, mode TEXT, asn TEXT, country TEXT, expert INTEGER, quic INTEGER, apps TEXT)`],
    [`CREATE TABLE IF NOT EXISTS crashes (id INTEGER PRIMARY KEY, t INTEGER, v TEXT, sdk INTEGER, model TEXT, trace TEXT)`],
    [`CREATE TABLE IF NOT EXISTS feedback (id INTEGER PRIMARY KEY, t INTEGER, v TEXT, sdk INTEGER, model TEXT, text TEXT, contact TEXT)`],
    [`CREATE TABLE IF NOT EXISTS devices (id TEXT PRIMARY KEY, first INTEGER, last INTEGER, v TEXT, code INTEGER, sdk INTEGER, model TEXT, maker TEXT, abi TEXT, lang TEXT, ram INTEGER, lite INTEGER, ip TEXT, country TEXT, city TEXT, isp TEXT, asn TEXT, net TEXT, ring TEXT DEFAULT 'stable', beta INTEGER DEFAULT 0, banned INTEGER DEFAULT 0, note TEXT, state TEXT, strategy TEXT, services TEXT, busy INTEGER DEFAULT 0, sessions INTEGER DEFAULT 0)`],
    [`CREATE INDEX IF NOT EXISTS devices_last ON devices(last)`],
    [`CREATE TABLE IF NOT EXISTS daily (day TEXT, id TEXT, v TEXT, PRIMARY KEY (day, id))`],
    [`CREATE TABLE IF NOT EXISTS peaks (day TEXT PRIMARY KEY, n INTEGER)`],
    [`CREATE TABLE IF NOT EXISTS events (id INTEGER PRIMARY KEY, t INTEGER, kind TEXT, level TEXT, ip TEXT, device TEXT, detail TEXT)`],
    [`CREATE INDEX IF NOT EXISTS events_t ON events(t)`],
    [`CREATE TABLE IF NOT EXISTS bans (kind TEXT, value TEXT, until INTEGER, reason TEXT, t INTEGER, PRIMARY KEY (kind, value))`],
    [`CREATE TABLE IF NOT EXISTS rollout (tag TEXT PRIMARY KEY, ring TEXT, percent INTEGER, paused INTEGER DEFAULT 0, pulled INTEGER DEFAULT 0, t INTEGER)`],
    [`CREATE TABLE IF NOT EXISTS updates (id INTEGER PRIMARY KEY, t INTEGER, device TEXT, from_v TEXT, to_v TEXT, result TEXT, detail TEXT)`],
    [`CREATE TABLE IF NOT EXISTS announcements (id INTEGER PRIMARY KEY, t INTEGER, title TEXT, text TEXT, level TEXT, ring TEXT, until INTEGER, active INTEGER DEFAULT 1)`],
    [`CREATE TABLE IF NOT EXISTS flags (k TEXT PRIMARY KEY, v TEXT, t INTEGER)`],
  ]);
}
