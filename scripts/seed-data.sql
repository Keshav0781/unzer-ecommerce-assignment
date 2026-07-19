-- scripts/seed-data.sql
-- Run manually after the app has started (so Flyway has created the schema).
-- Not a Flyway migration: this is demo data for local testing, not schema evolution.

INSERT INTO product (id, name, description, category)
VALUES ('00000000-0000-0000-0000-000000000001', 'Sample T-Shirt', 'A demo product for testing checkout', 'apparel');

INSERT INTO product_variant (id, product_id, variant_label, price_amount, price_currency)
VALUES ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'Default', 1999, 'EUR');

INSERT INTO stock (product_variant_id, quantity_total, quantity_reserved)
VALUES ('00000000-0000-0000-0000-000000000002', 10, 0);