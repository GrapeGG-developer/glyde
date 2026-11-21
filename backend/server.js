import http from 'node:http';
import { URL } from 'node:url';
import { migrate } from './lib/db.js';
import { send, readJson, router } from './lib/http.js';
import * as live from './services/live.js';
import { startJobs } from './services/jobs.js';
import { handleAdmin } from './admin/admin.js';
import updates from './routes/updates.js';
import strategies from './routes/strategies.js';
import telemetry from './routes/telemetry.js';

const PORT = process.env.PORT || 10000;
const route = router([updates, strategies, telemetry]);

async function handle(req, res) {
  const url = new URL(req.url, 'http://x');
  const path = url.pathname;
  const ip = live.clientIp(req);
  const host = req.headers['x-forwarded-host'] || req.headers.host;

  if (path === '/' || path === '/health') return send(res, 200, { ok: true, service: 'glyde-api', online: live.conns.size });
  if (path === '/admin' || path.startsWith('/admin/')) return handleAdmin(req, res, url, readJson);
  if (req.method === 'OPTIONS') return send(res, 204, '', { 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': 'GET, POST' });
  if (path.startsWith('/v1/admin/')) return send(res, 410, { error: 'use /admin/api' });

  const info = live.deviceInfo(req, url.searchParams);
  const banned = live.isBanned(ip, info.id);
  if (banned) return send(res, 403, { error: 'banned', reason: banned.reason || '' });
  if (!live.limit(ip, path === '/v3/check' ? 0.25 : 1)) return send(res, 429, { error: 'slow down' }, { 'Retry-After': '2' });

  const out = route({ req, res, url, path, ip, host, info });
  if (out === null) return send(res, 404, { error: 'not found' });
  return out;
}

const server = http.createServer((req, res) => {
  Promise.resolve()
    .then(() => handle(req, res))
    .catch(e => {
      if (!res.headersSent) send(res, 500, { error: 'internal', message: String(e.message || e).slice(0, 200) });
      else res.end();
    });
});
server.keepAliveTimeout = 65000;
server.headersTimeout = 70000;
server.requestTimeout = 0;

migrate()
  .then(() => live.loadState())
  .then(() => console.log('db ready'))
  .catch(e => console.error('init failed', e.message));
startJobs();
server.listen(PORT, '0.0.0.0', () => console.log('listening on ' + PORT));
