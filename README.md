# Unzer E-Commerce Shop, Vertical Slice

Checkout to payment to order-confirmation slice. See `ARCHITECTURE.md` for the full system design.

## What's real vs. stubbed

- **Credit Card, live**: real Unzer Java SDK call (`UnzerPaymentGateway`), verified to compile and start correctly against the real SDK jar. Never run against a real key, since none is available until the technical interview (see Architecture Document, section 1).
- **Open Banking, live**: same, via the SDK's `Pis` payment type.
- **Wero, stubbed**: no `Wero` class exists in the Unzer Java SDK (checked directly against the jar, both the version originally used and the latest available, 5.6.0). Throws a clear `UnsupportedOperationException` if selected.
- **Local development and all tests use `MockPaymentGateway`**, not the real SDK, controlled by one config property (`unzer.payment-gateway`), no code changes needed to swap.
- **No frontend or admin UI.** Catalog and customer accounts are designed (see the data model in `ARCHITECTURE.md`) but not built; this slice is checkout-only, guest-checkout only.
- **Refunds are designed, not implemented.** The `refund` table exists; no service uses it yet.
- **Payment reconciliation is live**: a scheduled job checks for orders stuck in `AWAITING_PAYMENT` past a threshold (10 minutes by default) and resolves them automatically, reusing the same webhook-processing logic. Configurable via `unzer.reconciliation.stale-threshold-minutes` and `unzer.reconciliation.interval-ms` in `application.properties`.

## Prerequisites

- Java 21
- Docker Desktop, running
- Git

Maven does not need to be installed separately, the included wrapper (`./mvnw`) downloads it automatically.

## Setup

**1. Clone the repository:**

```
git clone https://github.com/Keshav0781/unzer-ecommerce-assignment.git
cd unzer-ecommerce-assignment
```

**2. Start PostgreSQL:**

```
docker run --name unzer-postgres -e POSTGRES_PASSWORD=localdev -e POSTGRES_DB=unzer_shop -p 5432:5432 -d postgres:16
```

**3. Run the app** (this automatically applies the Flyway schema migration):

```
./mvnw spring-boot:run
```

Wait for `Started EcommerceShopApplication` in the logs.

**4. In a new terminal, load sample data** (a product, one variant at €19.99, 10 units in stock):

```
docker exec -i unzer-postgres psql -U postgres -d unzer_shop < scripts/seed-data.sql
```

## Try a checkout

```
curl -X POST http://localhost:8080/checkout \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-checkout-001",
    "customerId": null,
    "items": [{"productVariantId": "00000000-0000-0000-0000-000000000002", "quantity": 1}],
    "method": "CREDIT_CARD",
    "paymentToken": "mock-token"
  }'
```

Change `"idempotencyKey"` to a new value each time; the same key returns the same order (idempotency check).

## Simulating the webhook locally

The webhook receiver is a real endpoint (`POST /webhooks/unzer`). To exercise it manually, take the `unzer_transaction_id` from the `payment_attempt` row created by your checkout:

```
docker exec -it unzer-postgres psql -U postgres -d unzer_shop -c "SELECT unzer_transaction_id FROM payment_attempt;"
```

Then:

```
curl -i -X POST http://localhost:8080/webhooks/unzer \
  -H "Content-Type: application/json" \
  -d '{
    "event": "payment.completed",
    "publicKey": "s-pub-test",
    "paymentId": "<the-unzer_transaction_id-from-above>",
    "retrieveUrl": "https://api.unzer.com/v1/payments/<same-id>"
  }'
```

This returns `200 OK` and moves the order to `PAID`, confirm with:

```
docker exec -it unzer-postgres psql -U postgres -d unzer_shop -c "SELECT status FROM \"order\";"
```

**For a real, publicly reachable webhook URL** (needed for Unzer's sandbox to actually call this endpoint), expose local port 8080 with `ngrok http 8080` or a similar tool, then configure that URL in the Unzer merchant dashboard. Not set up in this submission, since no real Unzer account exists until the technical interview.

## Switching to the real Unzer SDK

Set in `application.properties`:

```
unzer.payment-gateway=live
unzer.api-key=<your-key>
```

No other code changes needed.

## Running the tests

Requires Docker running (tests use Testcontainers, a separate, temporary database, not your local `unzer-postgres`):

```
./mvnw test
```

24 tests, including `reserveStock_preventsOversellUnderConcurrency` (the oversell mechanism, two threads racing for the last unit), checkout idempotency, cross-module transaction rollback on partial failure, the full webhook-to-order-confirmation flow, and the reconciliation job resolving stale payments.