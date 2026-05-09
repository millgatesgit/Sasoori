# Graph Report - .  (2026-05-09)

## Corpus Check
- 102 files · ~2,876,639 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1056 nodes · 2064 edges · 55 communities (31 shown, 24 thin omitted)
- Extraction: 78% EXTRACTED · 22% INFERRED · 0% AMBIGUOUS · INFERRED: 462 edges (avg confidence: 0.79)
- Token cost: 27,000 input · 6,600 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Address Servlet Layer|Address Servlet Layer]]
- [[_COMMUNITY_Auth Servlet Layer|Auth Servlet Layer]]
- [[_COMMUNITY_Filters & Exceptions|Filters & Exceptions]]
- [[_COMMUNITY_Frontend API Client|Frontend API Client]]
- [[_COMMUNITY_Order DAO & Admin|Order DAO & Admin]]
- [[_COMMUNITY_Admin Products UI|Admin Products UI]]
- [[_COMMUNITY_User DAO & Admin|User DAO & Admin]]
- [[_COMMUNITY_Address DAO|Address DAO]]
- [[_COMMUNITY_Frontend API Modules|Frontend API Modules]]
- [[_COMMUNITY_Order API (Frontend)|Order API (Frontend)]]
- [[_COMMUNITY_Cart DAO & DTOs|Cart DAO & DTOs]]
- [[_COMMUNITY_Servlet Registry|Servlet Registry]]
- [[_COMMUNITY_Auth API (Frontend)|Auth API (Frontend)]]
- [[_COMMUNITY_Category Servlet|Category Servlet]]
- [[_COMMUNITY_Refresh Token DAO|Refresh Token DAO]]
- [[_COMMUNITY_Payment & Order Status|Payment & Order Status]]
- [[_COMMUNITY_Product Model|Product Model]]
- [[_COMMUNITY_Order Detail Page|Order Detail Page]]
- [[_COMMUNITY_Admin Dashboard & Users|Admin Dashboard & Users]]
- [[_COMMUNITY_Product DAO|Product DAO]]
- [[_COMMUNITY_Domain Models & JWT|Domain Models & JWT]]
- [[_COMMUNITY_Admin Shipments UI|Admin Shipments UI]]
- [[_COMMUNITY_Admin Orders UI|Admin Orders UI]]
- [[_COMMUNITY_Product Service & Cache|Product Service & Cache]]
- [[_COMMUNITY_Admin Invoices UI|Admin Invoices UI]]
- [[_COMMUNITY_Orders History Page|Orders History Page]]
- [[_COMMUNITY_User Auth Data Layer|User Auth Data Layer]]
- [[_COMMUNITY_App Bootstrap (DI & DB)|App Bootstrap (DI & DB)]]
- [[_COMMUNITY_Backend Config Concepts|Backend Config Concepts]]
- [[_COMMUNITY_AppConfig Singleton|AppConfig Singleton]]
- [[_COMMUNITY_Payment & Shipment Data|Payment & Shipment Data]]
- [[_COMMUNITY_Dev Proxy|Dev Proxy]]
- [[_COMMUNITY_OTP Verification|OTP Verification]]
- [[_COMMUNITY_Product Data Layer|Product Data Layer]]
- [[_COMMUNITY_Product Response DTOs|Product Response DTOs]]
- [[_COMMUNITY_PipeFix Agent|PipeFix Agent]]
- [[_COMMUNITY_Auth Response DTO|Auth Response DTO]]
- [[_COMMUNITY_Cart DTOs|Cart DTOs]]
- [[_COMMUNITY_Brand Identity|Brand Identity]]
- [[_COMMUNITY_Misc Frontend|Misc Frontend]]
- [[_COMMUNITY_Misc Backend|Misc Backend]]
- [[_COMMUNITY_Misc Backend|Misc Backend]]
- [[_COMMUNITY_Misc Backend|Misc Backend]]
- [[_COMMUNITY_Misc Backend|Misc Backend]]
- [[_COMMUNITY_Misc Backend|Misc Backend]]
- [[_COMMUNITY_Misc Backend|Misc Backend]]
- [[_COMMUNITY_Misc Backend|Misc Backend]]
- [[_COMMUNITY_Misc Backend|Misc Backend]]
- [[_COMMUNITY_Misc Backend|Misc Backend]]
- [[_COMMUNITY_Misc Backend|Misc Backend]]
- [[_COMMUNITY_Misc Backend|Misc Backend]]
- [[_COMMUNITY_Misc Backend|Misc Backend]]
- [[_COMMUNITY_Misc Backend|Misc Backend]]
- [[_COMMUNITY_Misc Backend|Misc Backend]]

## God Nodes (most connected - your core abstractions)
1. `Product` - 40 edges
2. `AdminServlet` - 29 edges
3. `showToast()` - 28 edges
4. `Order` - 28 edges
5. `formatPrice()` - 27 edges
6. `CartItem` - 24 edges
7. `User` - 24 edges
8. `Address` - 22 edges
9. `AuthServlet` - 19 edges
10. `OrderItem` - 18 edges

## Surprising Connections (you probably didn't know these)
- `CLAUDE.md Project Instructions` --references--> `Database Schema (schema.sql)`  [EXTRACTED]
  CLAUDE.md → src/main/resources/db/schema.sql
- `Startup Steps` --references--> `Database Schema (schema.sql)`  [EXTRACTED]
  startupsteps.txt → src/main/resources/db/schema.sql
- `CLAUDE.md Project Instructions` --references--> `Database Seed Data (seed.sql)`  [EXTRACTED]
  CLAUDE.md → src/main/resources/db/seed.sql
- `_itemHTML()` --calls--> `formatPrice()`  [EXTRACTED]
  frontend/src/pages/cart.js → frontend/src/utils/formatters.js
- `AddressDao` --references--> `DatabaseConfig (HikariCP Pool)`  [INFERRED]
  src/main/java/com/sasoori/dao/AddressDao.java → src/main/java/com/sasoori/config/DatabaseConfig.java

## Communities (55 total, 24 thin omitted)

### Community 0 - "Address Servlet Layer"
Cohesion: 0.05
Nodes (14): AddressServlet, AdminServlet, BaseServlet, CartServlet, CartDao, categories, products, HttpServlet (+6 more)

### Community 1 - "Auth Servlet Layer"
Cohesion: 0.05
Nodes (8): AuthServlet, OtpVerificationDao, ApiException, OtpVerification, RuntimeException, OtpService, TokenService, PasswordUtil

### Community 2 - "Filters & Exceptions"
Cohesion: 0.05
Nodes (11): Filter, CORSFilter, JWTAuthFilter, SecurityHeadersFilter, GoogleOAuthService, RazorpayService, ShiprocketService, SmsService (+3 more)

### Community 3 - "Frontend API Client"
Cohesion: 0.05
Nodes (53): api, ApiError, _drainQueue(), _refreshAccessToken(), _refreshQueue, request(), getCart(), removeCartItem() (+45 more)

### Community 4 - "Order DAO & Admin"
Cohesion: 0.05
Nodes (4): OrderDao, Order, OrderItem, OrderService

### Community 5 - "Admin Products UI"
Cohesion: 0.06
Nodes (39): _esc(), _loadCategories(), _loadProducts(), _onSubmitProduct(), page, _renderForm(), _renderList(), _renderPaging() (+31 more)

### Community 6 - "User DAO & Admin"
Cohesion: 0.06
Nodes (3): UserDao, UserResponse, User

### Community 8 - "Frontend API Modules"
Cohesion: 0.17
Nodes (33): Admin API (adminApi.js), API Client (apiClient.js), Auth API (authApi.js), Cart API (cartApi.js), Order/Address/Payment API (orderApi.js), Product API (productApi.js), User Profile API (userApi.js), App Bootstrap (app.js) (+25 more)

### Community 9 - "Order API (Frontend)"
Cohesion: 0.11
Nodes (24): createAddress(), getAddresses(), getConfig(), placeOrder(), verifyPayment(), _addressSection(), _bindAddressEvents(), _bindCtaEvents() (+16 more)

### Community 10 - "Cart DAO & DTOs"
Cohesion: 0.07
Nodes (3): CartItemDto, CartResponse, CartItem

### Community 11 - "Servlet Registry"
Cohesion: 0.12
Nodes (31): AddressServlet, AdminServlet, AuthServlet, BaseServlet, CartServlet, CategoryServlet, CLAUDE.md Project Instructions, ConfigServlet (+23 more)

### Community 12 - "Auth API (Frontend)"
Cohesion: 0.11
Nodes (20): loginWithPassword(), registerWithPassword(), sendOtp(), testLogin(), verifyOtp(), _formatCountdown(), _generateChallenge(), _generateState() (+12 more)

### Community 13 - "Category Servlet"
Cohesion: 0.08
Nodes (3): CategoryServlet, CategoryDao, Category

### Community 15 - "Payment & Order Status"
Cohesion: 0.11
Nodes (3): PaymentDao, ShipmentDao, PaymentServlet

### Community 17 - "Order Detail Page"
Cohesion: 0.17
Nodes (17): _renderDashboard(), getOrder(), _bindActions(), _esc(), _itemRow(), _load(), page, _parseAddress() (+9 more)

### Community 18 - "Admin Dashboard & Users"
Cohesion: 0.16
Nodes (16): page, _closeUserDetail(), _esc(), _load(), _openUserDetail(), page, _renderList(), _renderPaging() (+8 more)

### Community 20 - "Domain Models & JWT"
Cohesion: 0.16
Nodes (18): JWT Auth Filter, Address Model, Cart Item Model, Category Model, Order Model, Order Item Model, OTP Verification Model, Product Model (+10 more)

### Community 21 - "Admin Shipments UI"
Cohesion: 0.18
Nodes (11): _createShipment(), _load(), page, _renderList(), _renderPaging(), SHIP_STATUSES, _state, _toggleTracking() (+3 more)

### Community 22 - "Admin Orders UI"
Cohesion: 0.18
Nodes (14): _shell(), _closeDetail(), _esc(), _load(), _openDetail(), page, _renderList(), _renderPaging() (+6 more)

### Community 24 - "Admin Invoices UI"
Cohesion: 0.24
Nodes (11): _buildInvoiceHTML(), _closePreview(), _esc(), _load(), _openPreview(), page, _renderList(), _renderPaging() (+3 more)

### Community 25 - "Orders History Page"
Cohesion: 0.26
Nodes (11): cancelOrder(), getOrders(), _bindActions(), _load(), _orderCard(), page, _renderList(), _renderPaging() (+3 more)

### Community 27 - "User Auth Data Layer"
Cohesion: 0.18
Nodes (11): RefreshTokenDao, UserDao, refresh_tokens table, users table, AuthResponse, PasswordLoginRequest, PasswordRegisterRequest, UpdateProfileRequest (+3 more)

### Community 28 - "App Bootstrap (DI & DB)"
Cohesion: 0.22
Nodes (3): AppContextListener, DatabaseConfig, ServletContextListener

### Community 29 - "Backend Config Concepts"
Cohesion: 0.31
Nodes (10): HikariCP Connection Pool, Manual Dependency Injection via ServletContext, SERIALIZABLE Stock Check Pattern, AddressDao, AppConfig (Backend Config Singleton), AppContextListener (DI Bootstrap), CartDao, CategoryDao (+2 more)

### Community 31 - "Payment & Shipment Data"
Cohesion: 0.33
Nodes (6): PaymentDao, ShipmentDao, orders table, payments table, shipments table, PaymentVerifyRequest

### Community 32 - "Dev Proxy"
Cohesion: 0.4
Nodes (4): http, httpProxy, proxy, server

### Community 33 - "OTP Verification"
Cohesion: 0.4
Nodes (5): OtpVerificationDao, otp_verifications table, OtpSendRequest, OtpVerifyRequest, OtpVerification Model

### Community 34 - "Product Data Layer"
Cohesion: 0.4
Nodes (5): ProductDao, products table, ProductResponse, Product Model, JsonUtil

### Community 38 - "Cart DTOs"
Cohesion: 0.67
Nodes (3): CartItemRequest, CartResponse, CartItem Model

### Community 39 - "Brand Identity"
Cohesion: 1.0
Nodes (3): Frontend index.html (SPA Shell), Saroori Logo (Light Background), Saroori Logo (Dark/Reverse Background)

## Knowledge Gaps
- **99 isolated node(s):** `http`, `httpProxy`, `proxy`, `server`, `routes` (+94 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **24 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Order` connect `Order DAO & Admin` to `Filters & Exceptions`?**
  _High betweenness centrality (0.025) - this node is a cross-community bridge._
- **What connects `http`, `httpProxy`, `proxy` to the rest of the system?**
  _99 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Address Servlet Layer` be split into smaller, more focused modules?**
  _Cohesion score 0.05 - nodes in this community are weakly interconnected._
- **Should `Auth Servlet Layer` be split into smaller, more focused modules?**
  _Cohesion score 0.05 - nodes in this community are weakly interconnected._
- **Should `Filters & Exceptions` be split into smaller, more focused modules?**
  _Cohesion score 0.05 - nodes in this community are weakly interconnected._
- **Should `Frontend API Client` be split into smaller, more focused modules?**
  _Cohesion score 0.05 - nodes in this community are weakly interconnected._
- **Should `Order DAO & Admin` be split into smaller, more focused modules?**
  _Cohesion score 0.05 - nodes in this community are weakly interconnected._