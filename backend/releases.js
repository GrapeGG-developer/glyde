import crypto from 'node:crypto';
import { sqlSafe } from './db.js';

export const REPO = process.env.GITHUB_REPO || 'GrapeGG-developer/glyde';
const GH_TOKEN = process.env.GH_TOKEN || '';
const ABIS = ['arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86', 'universal'];
export const CODENAMES = ['Andromeda', 'Betelgeuse', 'Cassiopeia', 'Draco', 'Eridanus', 'Fornax', 'Gemini', 'Hydra', 'Izar', 'Juno', 'Kepler', 'Lyra', 'Mira', 'Nova', 'Orion', 'Pulsar', 'Quasar', 'Rigel', 'Sirius', 'Triton', 'Ursa', 'Vega', 'Wezen', 'Xena', 'Yildun', 'Zenith'];
export const RING_RANK = { insider: 0, beta: 1, stable: 2 };

const cache = new Map();

export async function cached(key, ttl, fn) {
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

export function dropCache(key) {
  cache.delete(key);
}

export function codenameFor(y, m) {
  return CODENAMES[(((y - 26) * 12 + (m - 11)) % CODENAMES.length + CODENAMES.length) % CODENAMES.length];
}

export function parseVersion(v) {
  const s = String(v || '').replace(/^v/, '');
  const n2 = s.match(/^Y(\d+)\.M(\d+)\.W(\d+)\.B(\d+)(?:-([A-Z][a-z]+))?(?:-(Insider|Beta|Dev|RC))?$/i);
  if (n2) {
    const y = +n2[1], mo = +n2[2], w = +n2[3], b = +n2[4];
    let stage = (n2[6] || 'release').toLowerCase();
    if (stage === 'rc') stage = 'beta';
    return { fresh: true, major: y, minor: mo, week: w, patch: b, build: b, stage, n: 0, codename: n2[5] || codenameFor(y, mo), code: y * 1e7 + mo * 1e5 + w * 1e4 + Math.min(b, 9999), s };
  }
  const m = s.match(/^(\d+)\.(\d+)(?:\.(\d+))?(?:-(insider|beta|rc|dev)\.?(\d+)?)?/i);
  if (!m) return null;
  const major = +m[1];
  const minor = +m[2];
  const patch = +(m[3] || 0);
  const stage = (m[4] || 'release').toLowerCase();
  const n = +(m[5] || 0);
  const stageCode = stage === 'insider' ? 10 + Math.min(n, 29) : stage === 'beta' || stage === 'rc' ? 40 + Math.min(n, 39) : stage === 'dev' ? 1 : 99;
  return { fresh: false, major, minor, patch, stage, n, code: major * 1e6 + minor * 1e4 + patch * 100 + stageCode, s };
}

export function ringOfTag(tag) {
  const p = parseVersion(tag);
  if (!p) return 'stable';
  if (p.stage === 'insider' || p.stage === 'dev') return 'insider';
  if (p.stage === 'beta' || p.stage === 'rc') return 'beta';
  return 'stable';
}

export function displayName(version, codename) {
  const p = parseVersion(version);
  if (!p) return 'Glyde ' + version;
  if (p.fresh) return 'Glyde ' + p.s;
  if (p.major < 20) return 'GT Unblock ' + p.s;
  let name = `Glyde ${p.major}.${String(p.minor).padStart(2, '0')}`;
  if (codename) name += ' ' + codename;
  if (p.stage === 'insider') name += ' Insider ' + (p.n || 1);
  else if (p.stage === 'beta' || p.stage === 'rc') name += ' Beta ' + (p.n || 1);
  else if (p.patch > 0) name += ' Patch ' + p.patch;
  return name;
}

function gh(path) {
  const headers = { 'User-Agent': 'glyde-api', Accept: 'application/vnd.github+json' };
  if (GH_TOKEN) headers.Authorization = 'Bearer ' + GH_TOKEN;
  return fetch('https://api.github.com/repos/' + REPO + path, { headers }).then(r => {
    if (!r.ok) throw new Error('github ' + r.status);
    return r.json();
  });
}

function decodeEntities(t) {
  return t.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&amp;/g, '&');
}

function prefixFor(version) {
  const p = parseVersion(version);
  return p && p.major >= 20 ? 'Glyde' : 'GT-Unblock';
}

async function releasesFromAtom() {
  const r = await fetch(`https://github.com/${REPO}/releases.atom`, { headers: { 'User-Agent': 'glyde-api' } });
  if (!r.ok) throw new Error('atom ' + r.status);
  const xml = await r.text();
  const entries = [...xml.matchAll(/<entry>([\s\S]*?)<\/entry>/g)].map(m => m[1]);
  return entries.map(e => {
    const link = (e.match(/<link[^>]*href="([^"]+)"/) || [])[1] || '';
    const tag = decodeURIComponent(link.split('/tag/')[1] || '');
    const title = decodeEntities((e.match(/<title>([\s\S]*?)<\/title>/) || [])[1] || '');
    const html = decodeEntities((e.match(/<content[^>]*>([\s\S]*?)<\/content>/) || [])[1] || '');
    const body = html.replace(/<li>/g, '- ').replace(/<\/(p|li|h\d)>/g, '\n').replace(/<br\s*\/?>/g, '\n').replace(/<[^>]+>/g, '').replace(/\n{3,}/g, '\n\n').trim();
    const version = tag.replace(/^v/, '');
    const prefix = prefixFor(version);
    return {
      tag_name: tag,
      name: title,
      draft: false,
      prerelease: /^Y/.test(version) ? /-(Insider|Beta|Dev|RC)$/i.test(version) : /-/.test(version),
      body,
      published_at: (e.match(/<updated>([^<]+)<\/updated>/) || [])[1],
      assets: ABIS.map(a => ({
        name: `${prefix}-${version}-${a}.apk`,
        browser_download_url: `https://github.com/${REPO}/releases/download/${tag}/${prefix}-${version}-${a}.apk`,
        size: 0,
      })),
    };
  }).filter(x => x.tag_name);
}

export async function githubReleases() {
  return cached('releases', 60 * 1000, async () => {
    try {
      return await gh('/releases?per_page=30');
    } catch (e) {
      return await releasesFromAtom();
    }
  });
}

function codenameOf(rel) {
  const pv = parseVersion(rel.tag_name);
  if (pv && pv.fresh) return pv.codename;
  const m = String(rel.name || '').match(/Glyde\s+[\d.]+\s+([A-Z][a-z]+)/);
  if (m && CODENAMES.includes(m[1])) return m[1];
  const b = String(rel.body || '').match(/^#?\s*([A-Z][a-z]+)\s*$/m);
  return b && CODENAMES.includes(b[1]) ? b[1] : '';
}

export async function controls() {
  return cached('rollout', 15 * 1000, async () => {
    const [rows] = await sqlSafe([[`SELECT * FROM rollout`]]);
    return new Map(rows.map(r => [r.tag, r]));
  });
}

export async function releaseList() {
  const [gh, ctl] = await Promise.all([githubReleases(), controls()]);
  return gh.filter(r => !r.draft && parseVersion(r.tag_name)).map(r => {
    const version = r.tag_name.replace(/^v/, '');
    const p = parseVersion(version);
    const c = ctl.get(r.tag_name) || {};
    const assets = {};
    const sizes = {};
    for (const a of r.assets || []) {
      if (a.name.endsWith('.apk')) {
        assets[a.name] = a.browser_download_url;
        if (a.size) sizes[a.name] = a.size;
      }
    }
    const codename = codenameOf(r);
    return {
      tag: r.tag_name,
      version,
      code: p.code,
      codename,
      display: displayName(version, codename),
      changelog: r.body || '',
      published: r.published_at,
      assets,
      sizes,
      ring: c.ring || ringOfTag(r.tag_name),
      percent: c.percent === undefined || c.percent === null ? 100 : Number(c.percent),
      paused: Number(c.paused || 0) === 1,
      pulled: Number(c.pulled || 0) === 1,
    };
  }).sort((a, b) => b.code - a.code);
}

export function bucket(id) {
  return parseInt(crypto.createHash('sha256').update(String(id)).digest('hex').slice(0, 8), 16) % 100;
}

export function allowed(rel, dev) {
  if (rel.pulled) return false;
  const dr = RING_RANK[dev.ring] ?? 2;
  const rr = RING_RANK[rel.ring] ?? 2;
  if (dr > rr) return false;
  if (dr === 0) return true;
  if (rel.paused) return false;
  if (dr === 2 && dev.id && bucket(dev.id) >= rel.percent) return false;
  if (dr === 2 && !dev.id && rel.percent < 100) return false;
  return true;
}

export function assetFor(rel, abi) {
  const prefix = prefixFor(rel.version);
  const want = `${prefix}-${rel.version}-${abi}.apk`;
  if (rel.assets[want]) return want;
  return `${prefix}-${rel.version}-universal.apk`;
}

export function offerPayload(rel, abi, host) {
  const asset = assetFor(rel, abi);
  return {
    version: rel.version,
    code: rel.code,
    codename: rel.codename,
    display: rel.display,
    ring: rel.ring,
    changelog: rel.changelog,
    published: rel.published,
    asset,
    url: rel.assets[asset] || '',
    size: rel.sizes[asset] || 0,
    sizes: rel.sizes,
    assets: rel.assets,
    mirror: `https://${host}/v1/apk/${encodeURIComponent(rel.tag)}/${encodeURIComponent(asset)}`,
  };
}

export async function offerFor(dev) {
  const list = await releaseList();
  for (const rel of list) {
    if (!allowed(rel, dev)) continue;
    if (dev.code && rel.code <= dev.code) return null;
    return rel;
  }
  return null;
}
