package com.racehorse.wallet;

import tools.jackson.databind.JsonNode;
import java.time.Instant;

public interface PayosGateway {
    record Link(String checkoutUrl, String qrCode, String paymentLinkId) { }
    record Status(String status, long amountVnd) { }
    Link create(long orderCode, long amountVnd, String description, Instant expiresAt);
    Status getStatus(long orderCode);
    boolean verifyWebhook(JsonNode body);
    boolean isFake();
}
