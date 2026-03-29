-- ============================================================
-- SASOORI — PostgreSQL Schema
-- Run once against a fresh database:
--   psql -U sasoori -d sasoori_db -f schema.sql
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Users ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    google_sub  VARCHAR(255) UNIQUE,          -- NULL for phone-only users
    email       VARCHAR(255) UNIQUE,          -- NULL for phone-only users
    phone       VARCHAR(15) UNIQUE,           -- NULL for Google-only users
    name        VARCHAR(255),
    picture_url TEXT,
    role        VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER', -- CUSTOMER | ADMIN
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT users_identity CHECK (google_sub IS NOT NULL OR phone IS NOT NULL)
);

-- ── OTP Verifications (mobile login) ─────────────────────────
CREATE TABLE IF NOT EXISTS otp_verifications (
    id         BIGSERIAL PRIMARY KEY,
    phone      VARCHAR(15) NOT NULL,
    otp_hash   VARCHAR(64) NOT NULL,          -- SHA-256 of 6-digit OTP
    attempts   INT NOT NULL DEFAULT 0,
    is_used    BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ NOT NULL,          -- NOW() + 5 minutes
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_otp_phone ON otp_verifications(phone) WHERE NOT is_used;

-- ── Refresh Tokens ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) UNIQUE NOT NULL,  -- SHA-256 of raw token
    is_revoked  BOOLEAN NOT NULL DEFAULT FALSE,
    user_agent  TEXT,
    ip_address  INET,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_refresh_hash ON refresh_tokens(token_hash);

-- ── Categories ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS categories (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    image_url   TEXT,
    sort_order  INT NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE
);

-- ── Products ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS products (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id   INT NOT NULL REFERENCES categories(id),
    name          VARCHAR(255) NOT NULL,
    slug          VARCHAR(255) UNIQUE NOT NULL,
    sku           VARCHAR(100) UNIQUE NOT NULL,
    description   TEXT,
    ingredients   TEXT,
    weight_grams  INT,
    price_paise   INT NOT NULL,               -- ₹1 = 100 paise
    mrp_paise     INT,
    stock_qty     INT NOT NULL DEFAULT 0,
    images        JSONB,                       -- ["url1","url2"]
    tags          TEXT[],
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    is_featured   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_products_category ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_slug     ON products(slug);
CREATE INDEX IF NOT EXISTS idx_products_active   ON products(is_active) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_products_search
    ON products USING gin(to_tsvector('english', name || ' ' || COALESCE(description, '')));

-- ── Carts ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS carts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cart_items (
    id         SERIAL PRIMARY KEY,
    cart_id    UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    quantity   INT NOT NULL CHECK (quantity > 0),
    added_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(cart_id, product_id)
);

-- ── Addresses ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS addresses (
    id         SERIAL PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    phone      VARCHAR(15) NOT NULL,
    line1      TEXT NOT NULL,
    line2      TEXT,
    city       VARCHAR(100) NOT NULL,
    state      VARCHAR(100) NOT NULL,
    pincode    VARCHAR(6) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE
);

-- ── Orders ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS orders (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id),
    status            VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    -- PENDING → PAYMENT_INITIATED → PAID → PROCESSING → SHIPPED → DELIVERED
    -- → CANCELLED | REFUND_INITIATED → REFUNDED
    subtotal_paise    INT NOT NULL,
    shipping_paise    INT NOT NULL DEFAULT 0,
    discount_paise    INT NOT NULL DEFAULT 0,
    total_paise       INT NOT NULL,
    shipping_address  JSONB NOT NULL,          -- snapshot at order time
    razorpay_order_id VARCHAR(100),
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_orders_user   ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);

CREATE TABLE IF NOT EXISTS order_items (
    id               SERIAL PRIMARY KEY,
    order_id         UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id       UUID REFERENCES products(id) ON DELETE SET NULL,
    product_name     VARCHAR(255) NOT NULL,    -- snapshot
    product_sku      VARCHAR(100) NOT NULL,
    quantity         INT NOT NULL,
    unit_price_paise INT NOT NULL,
    total_paise      INT NOT NULL
);

-- ── Payments ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payments (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id              UUID UNIQUE NOT NULL REFERENCES orders(id),
    razorpay_order_id     VARCHAR(100) NOT NULL,
    razorpay_payment_id   VARCHAR(100),
    razorpay_signature    TEXT,
    amount_paise          INT NOT NULL,
    currency              VARCHAR(10) NOT NULL DEFAULT 'INR',
    status                VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    -- CREATED → CAPTURED | FAILED | REFUNDED
    method                VARCHAR(50),
    webhook_payload       JSONB,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Shipments ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS shipments (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id               UUID UNIQUE NOT NULL REFERENCES orders(id),
    shiprocket_order_id    VARCHAR(100),
    shiprocket_shipment_id VARCHAR(100),
    awb_code               VARCHAR(100),
    courier_name           VARCHAR(100),
    tracking_url           TEXT,
    status                 VARCHAR(50),
    estimated_delivery     DATE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── WhatsApp Notifications ────────────────────────────────────
CREATE TABLE IF NOT EXISTS whatsapp_notifications (
    id            BIGSERIAL PRIMARY KEY,
    user_id       UUID REFERENCES users(id) ON DELETE SET NULL,
    phone         VARCHAR(15) NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    payload       JSONB,
    status        VARCHAR(30) NOT NULL DEFAULT 'SENT', -- SENT | FAILED | DELIVERED
    wa_message_id VARCHAR(100),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Audit Log ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID REFERENCES users(id) ON DELETE SET NULL,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   VARCHAR(100),
    old_value   JSONB,
    new_value   JSONB,
    ip_address  INET,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_log(user_id);

-- ── Updated-at trigger (reusable) ────────────────────────────
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE TRIGGER trg_products_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE TRIGGER trg_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE TRIGGER trg_payments_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE TRIGGER trg_shipments_updated_at
    BEFORE UPDATE ON shipments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
