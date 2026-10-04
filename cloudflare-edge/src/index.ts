export interface Env {
  DB: D1Database;
  ORIGIN_URL: string;
  ORIGIN_SECRET: string;
}

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Requested-With",
};

function jsonResponse(data: unknown, status = 200, extraHeaders: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json",
      ...CORS_HEADERS,
      ...extraHeaders,
    },
  });
}

function toPluginResponse(row: any) {
  return {
    id: row.id,
    name: row.name,
    description: row.description || "",
    baseUrl: row.base_url,
    version: row.version,
    author: row.author || "anonymous",
    channel: row.channel,
    likes: row.likes || 0,
    dislikes: row.dislikes || 0,
    isActive: Boolean(row.is_active),
    updatedAt: row.updated_at || new Date().toISOString(),
  };
}

async function upsertPluginInD1(db: D1Database, p: any) {
  const query = `
    INSERT INTO plugins (id, name, description, base_url, version, author, channel, likes, dislikes, is_active, code_file_path, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(id) DO UPDATE SET
      name = excluded.name,
      description = excluded.description,
      base_url = excluded.base_url,
      version = excluded.version,
      author = excluded.author,
      channel = excluded.channel,
      likes = excluded.likes,
      dislikes = excluded.dislikes,
      is_active = excluded.is_active,
      code_file_path = excluded.code_file_path,
      updated_at = excluded.updated_at
  `;
  await db.prepare(query).bind(
    p.id,
    p.name,
    p.description || "",
    p.baseUrl || "",
    p.version || "1.0.0",
    p.author || "anonymous",
    p.channel || "STABLE",
    p.likes || 0,
    p.dislikes || 0,
    p.isActive !== false ? 1 : 0,
    p.codeFilePath || `plugins/${p.id}/index.js`,
    p.updatedAt || new Date().toISOString()
  ).run();
}

async function syncAllFromOrigin(env: Env): Promise<number> {
  let count = 0;
  const activeIds = new Set<string>();
  let successfulChannels = 0;

  for (const channel of ["STABLE", "NIGHTLY"]) {
    try {
      const originRes = await fetch(`${env.ORIGIN_URL}/api/v1/plugins?channel=${channel}&size=100`, {
        headers: { "X-Origin-Secret": env.ORIGIN_SECRET },
      });
      if (originRes.ok) {
        successfulChannels++;
        const data: any = await originRes.json();
        const items = data.content || [];
        for (const item of items) {
          activeIds.add(item.id);
          await upsertPluginInD1(env.DB, item);
          count++;
        }
      }
    } catch (err) {
      console.error(`Sync error for channel ${channel}:`, err);
    }
  }

  // Prune deleted or deactivated plugins from D1 if both origin channels were queried successfully
  if (successfulChannels === 2) {
    try {
      const existing = await env.DB.prepare("SELECT id FROM plugins").all();
      for (const row of (existing.results || [])) {
        const id = String(row.id);
        if (!activeIds.has(id)) {
          await env.DB.prepare("DELETE FROM plugins WHERE id = ?").bind(id).run();
        }
      }
    } catch (err) {
      console.error("D1 pruning error:", err);
    }
  }

  return count;
}

export default {
  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(syncAllFromOrigin(env));
  },

  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;

    // Handle preflight CORS requests
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: CORS_HEADERS,
      });
    }

    // Completely block access to /api/v1/admin
    if (path.startsWith("/api/v1/admin")) {
      return jsonResponse({ error: "Not Found", message: "Path not found" }, 404);
    }

    // Edge Health Check
    if (path === "/" || path === "/health") {
      return jsonResponse({
        status: "ok",
        service: "aggregator-registry-edge",
        edgeColo: request.cf?.colo || "local",
        timestamp: new Date().toISOString(),
      });
    }

    // Edge Sync Route (trigger refresh of D1 cache from origin)
    if (path === "/api/v1/edge/sync" && request.method === "POST") {
      const syncedCount = await syncAllFromOrigin(env);
      return jsonResponse({ status: "synced", count: syncedCount });
    }

    // GET /api/v1/plugins - Fast Edge listing with D1
    if (path === "/api/v1/plugins" && request.method === "GET") {
      const channel = (url.searchParams.get("channel") || "STABLE").toUpperCase();
      const query = url.searchParams.get("query");
      const page = parseInt(url.searchParams.get("page") || "0", 10);
      const size = Math.min(parseInt(url.searchParams.get("size") || "20", 10), 100);
      const offset = page * size;

      try {
        let sql = `SELECT * FROM plugins WHERE is_active = 1`;
        const params: any[] = [];

        if (channel === "STABLE" || channel === "NIGHTLY") {
          sql += ` AND channel = ?`;
          params.push(channel);
        }

        if (query && query.trim()) {
          sql += ` AND (name LIKE ? OR description LIKE ? OR id LIKE ?)`;
          const term = `%${query.trim()}%`;
          params.push(term, term, term);
        }

        const countQuery = sql.replace("SELECT *", "SELECT COUNT(*) as total");
        const countRes = await env.DB.prepare(countQuery).bind(...params).first<{ total: number }>();
        const total = countRes?.total || 0;

        // If D1 is empty, fallback to origin and seed D1 in background
        if (total === 0) {
          const originRes = await fetch(`${env.ORIGIN_URL}${url.pathname}${url.search}`, {
            headers: { "X-Origin-Secret": env.ORIGIN_SECRET },
          });
          if (originRes.ok) {
            ctx.waitUntil(syncAllFromOrigin(env));
            const originData = await originRes.json();
            return jsonResponse(originData, 200, {
              "Cache-Control": "public, max-age=30, s-maxage=60",
            });
          }
        }

        sql += ` ORDER BY likes DESC LIMIT ? OFFSET ?`;
        params.push(size, offset);

        const rows = await env.DB.prepare(sql).bind(...params).all();
        const content = (rows.results || []).map(toPluginResponse);

        const totalPages = Math.ceil(total / size);
        return jsonResponse({
          content,
          page,
          size,
          totalElements: total,
          totalPages,
          last: page >= totalPages - 1,
        }, 200, {
          "Cache-Control": "public, max-age=30, s-maxage=60",
        });
      } catch (err: any) {
        console.error("D1 query error, falling back to origin:", err);
        return proxyToOrigin(request, env);
      }
    }

    // GET /api/v1/plugins/:id - Plugin details
    const pluginDetailMatch = path.match(/^\/api\/v1\/plugins\/([a-zA-Z0-9_-]+)$/);
    if (pluginDetailMatch && request.method === "GET") {
      const pluginId = pluginDetailMatch[1];
      try {
        const row = await env.DB.prepare("SELECT * FROM plugins WHERE id = ?").bind(pluginId).first();
        if (row) {
          return jsonResponse(toPluginResponse(row), 200, {
            "Cache-Control": "public, max-age=60, s-maxage=120",
          });
        }
      } catch (err) {
        console.error("D1 fetch single plugin error:", err);
      }
      return proxyToOrigin(request, env);
    }

    // GET /api/v1/plugins/:id/download - Edge-cached plugin script
    const downloadMatch = path.match(/^\/api\/v1\/plugins\/([a-zA-Z0-9_-]+)\/download$/);
    if (downloadMatch && request.method === "GET") {
      const cache = caches.default;
      const cacheKey = new Request(url.toString(), request);
      let response = await cache.match(cacheKey);

      if (!response) {
        const originUrl = `${env.ORIGIN_URL}${path}`;
        const originRes = await fetch(originUrl, {
          headers: { "X-Origin-Secret": env.ORIGIN_SECRET },
        });

        if (!originRes.ok) {
          return new Response(originRes.body, {
            status: originRes.status,
            headers: CORS_HEADERS,
          });
        }

        const body = await originRes.text();
        response = new Response(body, {
          status: 200,
          headers: {
            "Content-Type": "application/javascript; charset=utf-8",
            "Cache-Control": "public, max-age=3600, s-maxage=86400",
            ...CORS_HEADERS,
          },
        });

        ctx.waitUntil(cache.put(cacheKey, response.clone()));
      }

      return response;
    }

    // POST /api/v1/plugins - Submit plugin (forward to origin, update D1 on success)
    if (path === "/api/v1/plugins" && request.method === "POST") {
      const originRes = await proxyToOrigin(request, env);
      if (originRes.ok) {
        try {
          const clone = originRes.clone();
          const p = await clone.json();
          ctx.waitUntil(upsertPluginInD1(env.DB, p));
        } catch (e) {
          console.error("Failed to parse plugin submission response for D1 sync:", e);
        }
      }
      return originRes;
    }

    // POST /api/v1/plugins/:id/rate - Rate plugin (forward to origin, update D1 count)
    const rateMatch = path.match(/^\/api\/v1\/plugins\/([a-zA-Z0-9_-]+)\/rate$/);
    if (rateMatch && request.method === "POST") {
      const pluginId = rateMatch[1];
      const originRes = await proxyToOrigin(request, env);
      if (originRes.ok) {
        try {
          const clone = originRes.clone();
          const voteData: any = await clone.json();
          if (voteData && typeof voteData.likes === "number" && typeof voteData.dislikes === "number") {
            ctx.waitUntil(
              env.DB.prepare("UPDATE plugins SET likes = ?, dislikes = ? WHERE id = ?")
                .bind(voteData.likes, voteData.dislikes, pluginId)
                .run()
            );
          }
        } catch (e) {
          console.error("Failed to update D1 after rating:", e);
        }
      }
      return originRes;
    }

    // Fallback: proxy all other requests to origin with origin secret header
    return proxyToOrigin(request, env);
  },
};

async function proxyToOrigin(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const targetUrl = `${env.ORIGIN_URL}${url.pathname}${url.search}`;

  const headers = new Headers(request.headers);
  headers.set("X-Origin-Secret", env.ORIGIN_SECRET);
  headers.delete("host");

  const clientIp = request.headers.get("cf-connecting-ip") || request.headers.get("x-real-ip");
  if (clientIp) {
    headers.set("X-Forwarded-For", clientIp);
  }

  const init: RequestInit = {
    method: request.method,
    headers,
  };

  if (request.method !== "GET" && request.method !== "HEAD") {
    init.body = request.body;
    // @ts-ignore
    init.duplex = "half";
  }

  try {
    const originRes = await fetch(targetUrl, init);
    const newHeaders = new Headers(originRes.headers);
    for (const [key, val] of Object.entries(CORS_HEADERS)) {
      newHeaders.set(key, val);
    }
    return new Response(originRes.body, {
      status: originRes.status,
      headers: newHeaders,
    });
  } catch (err: any) {
    return jsonResponse({ error: "Bad Gateway", message: err.message }, 502);
  }
}
