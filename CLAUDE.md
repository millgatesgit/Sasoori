# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Dev Server

**Start the backend (port 9090):**
```bash
# Via preview_start (preferred — handles env vars automatically):
# Use: preview_start → "Backend (Tomcat via Maven)"

# Or manually from a bash shell:
bash E:/Work/Sasoori/start-dev.sh
```

The `start-dev.sh` script sets all required env vars and runs `mvn jetty:run`.

**⚠️ Windows JDK 17 NIO bug (this machine):** The `pipefix.jar` Java agent is mandatory. Without `-javaagent:E:/Work/Sasoori/pipefix.jar` in `MAVEN_OPTS`, Jetty cannot start because `Selector.open()` fails — `PipeImpl` attempts AF_UNIX loopback connect which returns `EINVAL` on this machine (Fortinet interference). The agent patches `sun.nio.ch.PipeImpl.noUnixDomainSockets = true` via `Unsafe`, forcing TCP loopback instead.

**Maven:** Bundled at `C:\Program Files\NetBeans-23\netbeans\java\maven\bin\mvn.cmd` (not in system PATH). Use `./mvnw` or `./mvnw.cmd` (wrapper is configured).

**Build WAR only:**
```bash
mvn package -DskipTests
```

**Dev test login** (only when `DEV_MODE=true`):
```bash
curl -X POST http://localhost:9090/api/v1/auth/test-login \
  -H "Content-Type: application/json" \
  -d '{"role":"ADMIN"}'
```

**Frontend:** Serve `frontend/` with any static file server (e.g. `npx serve frontend -p 3000`). No build step — plain ES modules loaded directly.

## Database

- PostgreSQL 15, service name: `postgresql-x64-15`
- DB: `sasoori_db`, user: `sasoori`, password: `sasoori123`
- Schema: `src/main/resources/db/schema.sql` (13 tables)
- Seed data: `src/main/resources/db/seed.sql` (4 categories, 30+ products)
- All monetary values are stored in **paise** (₹1 = 100 paise)

## Architecture

### Backend

**Dependency injection:** Manual singleton wiring — `AppContextListener.contextInitialized()` creates all DAOs/services in order and stores them in `ServletContext` via `ctx.setAttribute()`. Servlets retrieve dependencies in `init()` via `getServletContext().getAttribute()`. No IoC framework.

**Request lifecycle:**
```
Request → SecurityHeadersFilter → CORSFilter → JWTAuthFilter → Servlet → DAO/Service → JSON response
```

`JWTAuthFilter` validates the `Authorization: Bearer` header and sets `request.setAttribute("userId")` and `request.setAttribute("userRole")`. Public endpoints are whitelisted by path.

**Servlet routing:** Each servlet is registered with `@WebServlet("/api/v1/{resource}/*")`. Inside each servlet, `subPath(req)` strips the base path and a `switch` dispatches to handler methods. Pattern:
```java
String path = subPath(req);  // e.g. "/123" or "/slug/coconut-oil"
if (path.equals("") || path.equals("/")) { ... }
else if (path.startsWith("/slug/")) { ... }
```

**Error handling:** `BaseServlet.handleException()` maps `ApiException` to JSON `{"success":false,"error":{"code":"...", "message":"..."}}`. All servlets extend `BaseServlet`.

**OffsetDateTime serialization:** `JsonUtil.GSON` has a registered `TypeAdapter<OffsetDateTime>` (ISO_OFFSET_DATE_TIME). All timestamps in models must use `OffsetDateTime`, not `LocalDateTime`.

**Database arrays:** When binding PostgreSQL `TEXT[]` parameters, do NOT use `try-with-resources` on `ps.getConnection()` — it closes the shared connection. Use `ps.getConnection().createArrayOf("text", arr)` directly.

### Frontend

**Hash routing:** All navigation is `#/path`. `router.js` parses the hash, matches routes, lazily imports the page module, calls `page.render(container, params, query)`, then `page.onMount()`. Route guards check `store.getState().user` before rendering.

**API client (`src/api/apiClient.js`):** Single fetch wrapper with automatic token injection and 401→refresh→retry logic. In dev, `BASE_URL = 'http://localhost:9090/api/v1'`; in prod, `BASE_URL = '/api/v1'`. Concurrent refresh requests are queued on a single in-flight promise.

**State management (`src/store.js`):** Simple pub/sub store. Components subscribe via `store.subscribe(listener)`. Key state keys: `user`, `cart`, `cartCount`, `theme`.

**Page module contract:** Each page exports `{ render, onMount, onDestroy, meta }`. `render()` returns HTML string injected into the app container. `onMount()` attaches event listeners after DOM insertion.

### Key Domain Rules

- **Stock decrement** happens inside a `SERIALIZABLE` transaction in `OrderService` — stock is checked and decremented atomically to prevent overselling.
- **Prices are snapshots** — `order_items` stores `product_name` and `unit_price_paise` at time of order; product price changes don't affect existing orders.
- **Shipping address is a JSON snapshot** — stored as `JSONB` in `orders.shipping_address`, not a foreign key.
- **Refresh tokens are rotated** on every use. Reuse of a revoked token invalidates the entire family (all tokens for that user session).
- **Product `findById` vs `findByIdAdmin`** — `findById` filters `is_active = TRUE` (for storefront); `findByIdAdmin` fetches regardless of active status (for admin CRUD). Always use `findByIdAdmin` after create/update operations.

## Required Env Vars

| Variable | Default | Notes |
|----------|---------|-------|
| `DB_URL` | — | `jdbc:postgresql://localhost:5432/sasoori_db` |
| `DB_USER` / `DB_PASSWORD` | — | `sasoori` / `sasoori123` (dev) |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | — | Use `"dev"` locally (real creds for OAuth) |
| `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` | ephemeral | Generated at startup if not set |
| `FRONTEND_URL` | `http://localhost:5500` | Used for CORS |
| `SECURE_COOKIE` | `false` | Set `true` in production (HTTPS only) |
| `DEV_MODE` | `false` | Enables `/auth/test-login` endpoint |
| `RAZORPAY_*`, `SHIPROCKET_*`, `MSG91_*`, `FAST2SMS_API_KEY`, `WHATSAPP_*` | `""` | Optional in dev; features silently degrade |

## External HTTP Services

All 5 HTTP service classes (`GoogleOAuthService`, `RazorpayService`, `ShiprocketService`, `SmsService`, `WhatsAppService`) use `java.net.HttpURLConnection` — **not** `java.net.http.HttpClient`. The HttpClient constructor triggers `Selector.open()` which fails on this machine (same NIO bug as Jetty). Do not reintroduce `HttpClient`.

## Git

- **Remote:** `origin https://github.com/millgatesgit/Sasoori.git`
- **Default branch:** `main`

**Common commands:**
```bash
git add src/ frontend/ pom.xml CLAUDE.md   # stage specific files — never `git add .`
git commit -m "feat: describe change"
git push origin main
```

**`.gitignore` notes — what is NOT tracked:**
- `target/` — Maven build output (WAR, classes)
- `*.jar` — This excludes `pipefix.jar`. After a fresh clone you must rebuild it:
  ```bash
  cd pipefix/src
  javac PipeFixAgent.java
  jar cfm ../../pipefix.jar ../MANIFEST.MF PipeFixAgent.class
  ```
- `*.log`, `work/` — Runtime logs and Jetty work directory
- `.env` — Never commit secrets
- `yajsw/yajsw-stable-13.18/` — The YAJSW binary distribution is not tracked; only `yajsw/conf/` and the bat scripts are committed. Download YAJSW 13.18 separately if needed.

**After a fresh clone, to get the dev server running:**
1. Rebuild `pipefix.jar` (see above)
2. Start PostgreSQL: `net start postgresql-x64-15`
3. Run schema + seed: `psql -U sasoori sasoori_db -f src/main/resources/db/schema.sql`
4. Start backend: `bash start-dev.sh` or via `preview_start`
