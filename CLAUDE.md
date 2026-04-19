# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Business Context

**Sasoori** is a direct-to-consumer (D2C) e-commerce store selling homemade, traditionally made food products — cold pressed oils, masala powders, flours & grains, and health mixes. One family business manufactures all products themselves and sells directly to customers across India via this web app. The brand name is a family name.

**Core brand differentiators:** No Adulteration · Freshly Ground · Direct from Farmers · No Preservatives · Traditional Method · Cold Pressed

**Target audience:** Health-conscious Indian consumers seeking natural, additive-free alternatives to mass-produced food products.

**Geographic scope:** Pan-India delivery via Shiprocket courier integration.

**UI language:** Bilingual Tamil + English — the business originates from Tamil Nadu and serves both Tamil-speaking and general Indian customers.

### Product Catalogue

4 categories, ~40 SKUs total:

| Category | Slug | Products |
|----------|------|---------|
| Cold Pressed Oils | `oils` | Sesame Oil (100ml / 500ml / 1L), Coconut Oil (500ml / 1L), Groundnut Oil (1L) |
| Masala Powders | `masala` | Sambar, Chilli, Coriander, Turmeric, Curry Masala, Kara Masala, Chicken Masala, Dal Powder (various sizes) |
| Flours & Grains | `flours` | Wheat, Ragi, Gram, Pearl Millet, Sorghum, Small Millet Mix, Urad Dal (various sizes) |
| Health Mixes | `health` | Sasoori Health Mix, Cane Sugar, Groundnuts (500g) |

**SKU format:** `SAR-{CATEGORY}-{PRODUCT}-{WEIGHT}` — e.g. `SAR-OIL-SES-500`, `SAR-MAS-SAM-100`

### User Roles

| Role | Access |
|------|--------|
| `CUSTOMER` | Browse products, manage cart, place orders, manage addresses, view order history |
| `ADMIN` | All customer access + product CRUD, order status management, user management, shipment tracking, invoices, dashboard analytics |

---

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
- Schema: `src/main/resources/db/schema.sql` (13 tables + audit_log = 14 total)
- Seed data: `src/main/resources/db/seed.sql` (4 categories, 40 products)
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

**Error handling:** `BaseServlet.handleException()` maps `ApiException` to JSON `{"success":false,"error":{"code":"...", "message":"..."}}`. All servlets extend `BaseServlet`. Add new error codes to `ApiException` — never use raw strings — so errors remain traceable across logs and the frontend.

**OffsetDateTime serialization:** `JsonUtil.GSON` has a registered `TypeAdapter<OffsetDateTime>` (ISO_OFFSET_DATE_TIME). All timestamps in models must use `OffsetDateTime`, not `LocalDateTime`.

**Database arrays:** When binding PostgreSQL `TEXT[]` parameters, do NOT use `try-with-resources` on `ps.getConnection()` — it closes the shared connection. Use `ps.getConnection().createArrayOf("text", arr)` directly.

**Product list cache:** `ProductService` keeps a 60-second in-memory cache of paginated list results (keyed by `category|search|tag|sort|page|size`). The cache is invalidated on any product write (create/update/delete). Do not bypass this cache layer for storefront reads.

### Frontend

**Hash routing:** All navigation is `#/path`. `router.js` parses the hash, matches routes, lazily imports the page module, calls `page.render(container, params, query)`, then `page.onMount()`. Route guards check `store.getState().user` before rendering.

**API client (`src/api/apiClient.js`):** Single fetch wrapper with automatic token injection and 401→refresh→retry logic. In dev, `BASE_URL = 'http://localhost:9090/api/v1'`; in prod, `BASE_URL = '/api/v1'`. Concurrent refresh requests are queued on a single in-flight promise.

**State management (`src/store.js`):** Simple pub/sub store. Components subscribe via `store.subscribe(listener)`. Key state keys: `user`, `cart`, `cartCount`, `theme`.

**Page module contract:** Each page exports `{ render, onMount, onDestroy, meta }`. `render()` returns HTML string injected into the app container. `onMount()` attaches event listeners after DOM insertion.

### Key Domain Rules

- **Stock decrement** happens inside a `SERIALIZABLE` transaction in `OrderService` — stock is checked and decremented atomically to prevent overselling. The transaction retries once on serialization failure (SQLState `40001`).
- **Prices are snapshots** — `order_items` stores `product_name`, `product_sku`, and `unit_price_paise` at time of order; product price changes don't affect existing orders.
- **Shipping address is a JSON snapshot** — stored as `JSONB` in `orders.shipping_address`, not a foreign key.
- **Refresh tokens are rotated** on every use. Reuse of a revoked token invalidates the entire family (all tokens for that user session).
- **Product `findById` vs `findByIdAdmin`** — `findById` filters `is_active = TRUE` (for storefront); `findByIdAdmin` fetches regardless of active status (for admin CRUD). Always use `findByIdAdmin` after create/update operations.
- **Low stock alert threshold** — admin dashboard flags products with `stock_qty < 10`.

---

## Domain Model

### Order Lifecycle

```
PENDING                (order created, awaiting payment)
  ↓
PAYMENT_INITIATED      (Razorpay checkout opened by customer)
  ↓
PAID                   (payment captured, stock already decremented)
  ↓
PROCESSING             (admin has acknowledged, preparing for dispatch)
  ↓
SHIPPED                (handed to Shiprocket courier, AWB assigned)
  ↓
DELIVERED              (courier confirms delivery)

At PENDING or PAID only:
  ↓
CANCELLED              (user or admin cancels; stock restored)
  ↓
REFUND_INITIATED       (refund process started)
  ↓
REFUNDED               (refund confirmed)
```

State transitions are validated in `OrderDao.updateStatus()`. Only admin can move an order to `PROCESSING`, `SHIPPED`, or `DELIVERED`. Customers can cancel only while the order is `PENDING` or `PAID`.

### Pricing Rules

| Concept | Value | Storage |
|---------|-------|---------|
| Selling price | `price_paise` | Paise (₹1 = 100) |
| MRP (shown crossed out) | `mrp_paise` | Paise |
| Free shipping threshold | ₹499 (`49900` paise) | Configured in `AppConfig` |
| Standard shipping charge | ₹49 (`4900` paise) | Configured in `AppConfig` |
| Discount | `discount_paise` | Always `0` for now — reserved for future coupon system |

Discount calculation for display: `((mrp - price) / mrp) * 100` — computed at render time, not stored.

### Authentication Methods

Three independent login methods can be linked to the same user account (matched on phone / Google sub / email):

| Method | Identity field | Notes |
|--------|---------------|-------|
| Google OAuth (PKCE) | `google_sub` | RS256 ID token validation |
| Phone OTP | `phone` | 6-digit, 5 min expiry, max 3 attempts, rate-limited 2/5 min |
| Email + Password | `email` | PBKDF2 hash, never stored in plaintext |

Access tokens: RS256 JWT, 15 min TTL. Refresh tokens: 256-bit random, SHA-256 stored in DB, 7-day TTL, rotated on every use.

### Product Tags

Products use a `TEXT[]` tags column for merchandising and filtering:

| Tag | Meaning |
|-----|---------|
| `featured` | Shown in home page featured section |
| `bestseller` | "Best Seller" badge on category cards |
| `new` | "New" badge on product cards |
| `healthy` | Health-oriented filter |
| `glutenfree` | Gluten-free filter |
| Category tags (`oils`, `masala`, `flours`, `health`) | Match category slug for cross-filtering |

---

## Future Roadmap

These features are **not yet built** but should be designed in a way that makes them straightforward to add:

| Feature | Notes |
|---------|-------|
| Thermal paper shipping label printing | Print order + address in thermal format for packaging; likely a new admin endpoint generating a minimal HTML/PDF |
| Coupon / discount codes | `discount_paise` column in `orders` is already reserved; a `coupons` table and validation step in `OrderService.placeOrder()` needs to be added |
| Search enhancements | Full-text search exists on `name + description`; future work: typo tolerance, synonym expansion, search suggestions |

### Integrations Not Yet Finalised

The following integrations are wired but the **specific provider is not confirmed**. Services are intentionally abstracted behind a single class so the provider can be swapped without touching business logic:

| Integration | Current implementation | Status |
|-------------|----------------------|--------|
| Payment gateway | `RazorpayService` | Not finalised — may be replaced |
| OTP / login notifications | `SmsService` (msg91 + fast2sms dual-provider) + `WhatsAppService` | Not finalised — WhatsApp may become the primary channel; SMS is optional |
| Shipping | `ShiprocketService` | In use, likely to stay |

**When swapping a provider:** create a new service class with the same method signatures, update the wiring in `AppContextListener`, and update the relevant env vars in `Required Env Vars`. No other code should need to change.

---

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
