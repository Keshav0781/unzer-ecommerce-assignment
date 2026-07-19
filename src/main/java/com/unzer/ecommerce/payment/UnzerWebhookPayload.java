package com.unzer.ecommerce.payment;

// Matches Unzer's real webhook payload shape, verified against
// docs.unzer.com/server-side-integration/api-basics/notifications/
// Per Unzer's explicit instruction, "event" must never be used as the
// indicator of a resource's state; only paymentId is used here, to fetch
// the authoritative state via PaymentGateway.checkStatus().
public record UnzerWebhookPayload(String event, String publicKey, String paymentId, String retrieveUrl) {
}