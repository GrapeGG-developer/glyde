export function send(res, code, body, headers = {}) {
  const isObj = typeof body === 'object' && !Buffer.isBuffer(body);
  res.writeHead(code, {
    'Content-Type': isObj ? 'application/json; charset=utf-8' : 'text/plain; charset=utf-8',
    'Cache-Control': 'no-store',
    'Access-Control-Allow-Origin': '*',
    ...headers,
  });
  res.end(isObj ? JSON.stringify(body) : body);
}

export async function readJson(req, limit = 64 * 1024) {
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

export function router(routes) {
  const table = routes.flatMap(r => Object.entries(r).map(([key, fn]) => {
    const [method, path] = key.split(' ');
    return { method, path, re: path.includes(':') ? new RegExp('^' + path.replace(/:(\w+)/g, '(?<$1>[^/]+)') + '$') : null, fn };
  }));
  return ctx => {
    for (const r of table) {
      if (r.method !== ctx.req.method) continue;
      if (r.re) {
        const m = ctx.path.match(r.re);
        if (m) return r.fn({ ...ctx, params: Object.fromEntries(Object.entries(m.groups).map(([k, v]) => [k, decodeURIComponent(v)])) });
      } else if (r.path === ctx.path) return r.fn(ctx);
    }
    return null;
  };
}
