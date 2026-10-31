package com.racehorse.wallet;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;

public interface PayosGateway {
    record Link(String checkoutUrl, String qrCode, String paymentLinkId) { }
    record Status(String status, long amountVnd) { }
    Link create(long orderCode, long amountVnd, String description, Instant expiresAt);
    default Link create(long orderCode, long amountVnd, String description, Instant expiresAt, String ipAddress) {
        return create(orderCode, amountVnd, description, expiresAt);
    }
    Status getStatus(long orderCode);
    boolean verifyWebhook(JsonNode body);
    default boolean verifyVnpay(Map<String, String> parameters) { return false; }
    boolean isFake();
    default boolean isVnpay() { return false; }
}
