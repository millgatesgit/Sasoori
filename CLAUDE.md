# CLAUDE.md

## Business Context

**Sasoori** is a D2C e-commerce store — one family business manufactures and sells homemade food products (cold pressed oils, masala powders, flours, health mixes) directly to customers across India. Brand name is a family name.

- **UI:** Bilingual Tamil + English. **Mobile-first** — most customers are on smartphones.
- **Roles:** `CUSTOMER` (shop/orders/addresses) · `ADMIN` (+ product CRUD, order management, shipments, dashboard)
- **Categories:** `oils` · `masala` · `flours` · `health` (4 categories, ~40 SKUs)
- **SKU format:** `SAR-{CAT}-{PRODUCT}-{WEIGHT}` e.g. `SAR-OIL-SES-500`
- **Money:** All values stored in **paise** (₹1 = 100 paise)

## Dev Server

```bash
bash E:/Work/Sasoori/start-dev.sh   # sets env vars + runs mvn jetty:run (port 9090)
```

**⚠️ pipefix.jar is mandatory on this machine.** Without `-javaagent:.../pipefix.jar` in `MAVEN_OPTS`, Jetty fails — `Selector.open()` hits a Windows JDK 17 NIO bug (Fortinet interference). The agent forces TCP loopback instead of AF_UNIX. Use `./mvnw` (Maven not in PATH).

**Frontend:** `npx serve frontend -p 3000` — plain ES modules, no build step.

**Dev login** (`DEV_MODE=true` only): `POST /api/v1/auth/test-login` `{"role":"ADMIN"}`

## Database

- PostgreSQL 15 · DB: `sasoori_db` · user/pass: `sasoori`/`sasoori123`
- Schema: `src/main/resources/db/schema.sql` · Seed: `src/main/resources/db/seed.sql`

## Architecture

**Backend — request flow:**
```
Request → SecurityHeadersFilter → CORSFilter → JWTAuthFilter → Servlet → DAO/Service → JSON
```
`JWTAuthFilter` sets `request.setAttribute("userId"/"userRole")`. Manual DI: `AppContextListener` wires all DAOs/services into `ServletContext`; servlets pull them in `init()`. No IoC framework.

**Servlet routing:** `subPath(req)` strips base path; `switch` dispatches inside each servlet.

**Error handling:** All servlets extend `BaseServlet`. Errors → `ApiException` → `{"success":false,"error":{"code":"...","message":"..."}}`. Always add new codes to `ApiException`, never raw strings.

**Gotchas:**
- Timestamps: use `OffsetDateTime`, never `LocalDateTime` (`JsonUtil.GSON` has a registered adapter)
- PostgreSQL `TEXT[]`: do NOT `try-with-resources` on `ps.getConnection()` — use it directly to call `createArrayOf("text", arr)`
- HTTP clients: use `HttpURLConnection` only — `HttpClient` triggers the same NIO bug as Jetty. Never reintroduce it.
- Product list: `ProductService` has a 60s in-memory cache (key: `category|search|tag|sort|page|size`), invalidated on any write. Don't bypass it for storefront reads.

**Frontend:**
- Hash routing (`#/path`) — `router.js` lazy-loads page modules, calls `render()` then `onMount()`
- Page contract: `{ render, onMount, onDestroy, meta }` — `render()` returns HTML string, `onMount()` attaches listeners
- API client (`src/api/apiClient.js`): auto token injection + 401→refresh→retry; concurrent refreshes queued on one in-flight promise
- State: `src/store.js` pub/sub — keys: `user`, `cart`, `cartCount`, `theme`

## Domain Rules

**Order states:** `PENDING → PAYMENT_INITIATED → PAID → PROCESSING → SHIPPED → DELIVERED`
At `PENDING`/`PAID` only: `→ CANCELLED → REFUND_INITIATED → REFUNDED`
- Only admin can set `PROCESSING`, `SHIPPED`, `DELIVERED`
- Customer cancel: only at `PENDING` or `PAID`; stock is restored on cancel

**Pricing:**
- `price_paise` = sell price · `mrp_paise` = MRP (shown crossed out) · discount computed at render: `(mrp-price)/mrp*100`
- Free shipping ≥ ₹499 (`49900`); otherwise ₹49 (`4900`) — both in `AppConfig`
- `discount_paise` on orders is always `0` (reserved for future coupons)

**Other rules:**
- Stock check + decrement is `SERIALIZABLE` with one retry on SQLState `40001`
- `order_items` snapshots `product_name`, `product_sku`, `unit_price_paise` at order time — immutable
- `orders.shipping_address` is a JSONB snapshot, not a FK
- `findById` (storefront) filters `is_active=TRUE`; `findByIdAdmin` does not — always use admin variant after create/update
- Admin dashboard low-stock alert: `stock_qty < 10`
- Refresh tokens rotate on every use; replaying a revoked token revokes all sessions for that user
- Auth: Google OAuth (PKCE/RS256) · Phone OTP (6-digit, 5 min, 3 attempts, rate-limited) · Email+Password (PBKDF2)

**Unfinalized integrations** (abstracted for easy swapping — new class + rewire in `AppContextListener`):
- Payment: `RazorpayService` — not confirmed final
- OTP/notifications: `SmsService` (msg91+fast2sms) + `WhatsAppService` — WhatsApp may become primary; SMS optional
- Shipping: `ShiprocketService` — likely to stay

## Env Vars

| Var | Dev default |
|-----|------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/sasoori_db` |
| `DB_USER` / `DB_PASSWORD` | `sasoori` / `sasoori123` |
| `GOOGLE_CLIENT_ID/SECRET` | `"dev"` |
| `JWT_PRIVATE_KEY/PUBLIC_KEY` | ephemeral (generated at startup) |
| `FRONTEND_URL` | `http://localhost:5500` (CORS) |
| `SECURE_COOKIE` | `false` (set `true` in prod) |
| `DEV_MODE` | `false` |
| `RAZORPAY_*`, `SHIPROCKET_*`, `MSG91_*`, `FAST2SMS_API_KEY`, `WHATSAPP_*` | `""` (silently degrade) |

## Git

Remote: `https://github.com/millgatesgit/Sasoori.git` · Default branch: `main`

Stage specific files — never `git add .`:
```bash
git add src/ frontend/ pom.xml CLAUDE.md
```

**After fresh clone:** rebuild `pipefix.jar` (`cd pipefix/src && javac PipeFixAgent.java && jar cfm ../../pipefix.jar ../MANIFEST.MF PipeFixAgent.class`), start PostgreSQL, run schema.sql, then `bash start-dev.sh`.
