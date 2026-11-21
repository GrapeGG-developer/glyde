import { send, readJson } from '../lib/http.js';
import { sqlSafe } from '../lib/db.js';
import { REPO, cached, releaseList, allowed, offerPayload } from '../services/releases.js';
import * as live from '../services/live.js';

const { str } = live;
const SAFE = /^[\w.\-]+$/;

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

function buildHashes(tag) {
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

export default {
  'GET /v3/live': ({ req, res, info, ip, host }) => {
    if (!info.id) return send(res, 400, { error: 'id required' });
    return live.openLive(req, res, info, ip, host);
  },

  'GET /v3/check': async ({ res, info, host, ip }) => send(res, 200, await live.quickCheck(info, host, ip)),

  'POST /v3/update-result': async ({ req, res, info, ip }) => {
    const b = await readJson(req);
    await sqlSafe([[`INSERT INTO updates (t, device, from_v, to_v, result, detail) VALUES (?,?,?,?,?,?)`, [Date.now(), info.id, str(b.from, 40), str(b.to, 40), str(b.result, 20), str(b.detail, 500)]]]);
    live.pushAdmin('update_result', { id: info.id, ...b });
    if (b.result === 'failed') live.logEvent('update_failed', 'warn', ip, info.id, { to: b.to, detail: str(b.detail, 300) });
    return send(res, 200, { ok: true });
  },

  'GET /v3/build': async ({ res, url }) => {
    const tag = str(url.searchParams.get('tag'), 48);
    if (!tag || !SAFE.test(tag)) return send(res, 400, { error: 'bad tag' });
    try {
      return send(res, 200, { tag, sums: await buildHashes(tag), actions: `https://github.com/${REPO}/actions`, release: `https://github.com/${REPO}/releases/tag/${tag}` });
    } catch (e) {
      return send(res, 404, { error: 'no sums' });
    }
  },

  'GET /v1/version': async ({ res, url, host }) => {
    const o = await legacyUpdate(url, host);
    if (!o) return send(res, 404, { error: 'no release' });
    return send(res, 200, { v: o.version });
  },

  'GET /v1/update': async ({ res, url, host }) => {
    const o = await legacyUpdate(url, host);
    if (!o) return send(res, 404, { error: 'no release' });
    return send(res, 200, o);
  },

  'GET /v1/apk/:tag/:name': ({ req, res, params }) => {
    if (!SAFE.test(params.tag) || !SAFE.test(params.name) || !params.name.endsWith('.apk')) return send(res, 400, { error: 'bad name' });
    return streamApk(req, res, params.tag, params.name);
  },
};
