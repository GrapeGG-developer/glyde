import { send, readJson } from '../lib/http.js';
import { sql } from '../lib/db.js';
import * as live from '../services/live.js';

const { str, int } = live;

export default {
  'POST /v3/status': async ({ req, res, info, ip }) => {
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
  },

  'POST /v1/report': async ({ req, res }) => {
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
  },

  'POST /v1/ping': async ({ req, res, info, ip }) => {
    const b = await readJson(req);
    const d = b.device || {};
    await sql([[`INSERT INTO pings (t, v, sdk, model, maker, abi, lang, mode, asn, country, expert, quic, apps) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)`,
      [Date.now(), str(d.v, 40), int(d.sdk), str(d.model, 64), str(d.maker, 64), str(d.abi, 20), str(b.lang, 8), str(b.mode, 8), str(b.asn, 16), str(b.country, 4), b.expert ? 1 : 0, b.quic ? 1 : 0, str(b.apps, 8)]]]);
    if (info.id) await live.touchDevice(info, ip);
    return send(res, 200, { ok: true });
  },

  'POST /v1/crash': async ({ req, res }) => {
    const b = await readJson(req, 128 * 1024);
    const d = b.device || {};
    await sql([[`INSERT INTO crashes (t, v, sdk, model, trace) VALUES (?,?,?,?,?)`, [Date.now(), str(d.v, 40), int(d.sdk), str(d.model, 64), str(b.trace, 30000)]]]);
    live.pushAdmin('crash', { v: d.v, model: d.model });
    return send(res, 200, { ok: true });
  },

  'POST /v1/feedback': async ({ req, res }) => {
    const b = await readJson(req);
    const d = b.device || {};
    const text = str(b.text, 4000);
    if (!text || !text.trim()) return send(res, 400, { error: 'empty' });
    await sql([[`INSERT INTO feedback (t, v, sdk, model, text, contact) VALUES (?,?,?,?,?,?)`, [Date.now(), str(d.v, 40), int(d.sdk), str(d.model, 64), text, str(b.contact, 200)]]]);
    live.pushAdmin('feedback', { v: d.v });
    return send(res, 200, { ok: true });
  },
};
