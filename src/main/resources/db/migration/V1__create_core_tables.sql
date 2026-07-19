-- V1__create_core_tables.sql
-- Core tables for the checkout -> payment -> confirmation vertical slice.
-- customer/customer_address are intentionally NOT created here: the customer
-- module is out of scope for this slice (see Architecture Document, section 9).
-- Guest checkout is the only supported path; order.customer_id is a plain
-- nullable column with no foreign key, since there is no customer table yet.

CREATE TABLE product (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    description TEXT,
    category TEXT NOT NULL
);

CREATE INDEX idx_product_category ON product (category);

CREATE TABLE product_variant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES product (id),
    variant_label TEXT NOT NULL,
    price_amount INTEGER NOT NULL,
    price_currency TEXT NOT NULL
);

CREATE INDEX idx_product_variant_product_id ON product_variant (product_id);

CREATE TABLE stock (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_variant_id UUID NOT NULL UNIQUE REFERENCES product_variant (id),
    quantity_total INTEGER NOT NULL,
    quantity_reserved INTEGER NOT NULL DEFAULT 0,
    quantity_available INTEGER GENERATED ALWAYS AS (quantity_total - quantity_reserved) STORED
);

CREATE INDEX idx_stock_product_variant_id ON stock (product_variant_id);

CREATE TABLE "order" (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID,
    status TEXT NOT NULL CHECK (status IN (
        'CREATED', 'AWAITING_PAYMENT', 'PAID', 'PAYMENT_FAILED', 'CANCELLED',
        'FULFILLING', 'SHIPPED', 'COMPLETED', 'REFUNDED'
    )),
    total_amount INTEGER NOT NULL,
    total_currency TEXT NOT NULL,
    idempotency_key TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_customer_id ON "order" (customer_id);
CREATE INDEX idx_order_idempotency_key ON "order" (idempotency_key);

CREATE TABLE order_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES "order" (id),
    product_variant_id UUID NOT NULL REFERENCES product_variant (id),
    quantity INTEGER NOT NULL,
    price_at_purchase INTEGER NOT NULL
);

CREATE INDEX idx_order_item_order_id ON order_item (order_id);

CREATE TABLE payment_attempt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES "order" (id),
    method TEXT NOT NULL CHECK (method IN ('CREDIT_CARD', 'WERO', 'OPEN_BANKING')),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    idempotency_key TEXT NOT NULL UNIQUE,
    unzer_resource_id TEXT,
    unzer_transaction_id TEXT,
    webhook_received_count INTEGER NOT NULL DEFAULT 0,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_attempt_order_id ON payment_attempt (order_id);
CREATE INDEX idx_payment_attempt_idempotency_key ON payment_attempt (idempotency_key);

CREATE TABLE refund (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES "order" (id),
    payment_attempt_id UUID NOT NULL REFERENCES payment_attempt (id),
    amount INTEGER NOT NULL,
    unzer_refund_transaction_id TEXT,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_refund_order_id ON refund (order_id);
CREATE INDEX idx_refund_payment_attempt_id ON refund (payment_attempt_id);