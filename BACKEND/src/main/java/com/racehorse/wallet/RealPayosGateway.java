package com.racehorse.wallet;

import com.racehorse.common.exception.ExternalServiceException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.HexFormat;

final class RealPayosGateway implements PayosGateway {
    private static final URI API = URI.create("https://api-merchant.payos.vn/v2/payment-requests");
    private final ObjectMapper json;
    private final String clientId;
    private final String apiKey;
    private final String checksumKey;
    private final String returnUrl;
    private final String cancelUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    RealPayosGateway(ObjectMapper json, String clientId, String apiKey, String checksumKey,
                     String returnUrl, String cancelUrl) {
        this.json = json;
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.checksumKey = checksumKey;
        this.returnUrl = returnUrl;
        this.cancelUrl = cancelUrl;
    }

    @Override
    public Link create(long orderCode, long amountVnd, String description, Instant expiresAt) {
        String source = "amount=" + amountVnd + "&cancelUrl=" + cancelUrl + "&description=" + description
                + "&orderCode=" + orderCode + "&returnUrl=" + returnUrl;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", orderCode);
        body.put("amount", amountVnd);
        body.put("description", description);
        body.put("cancelUrl", cancelUrl);
        body.put("returnUrl", returnUrl);
        body.put("expiredAt", Math.toIntExact(expiresAt.getEpochSecond()));
        body.put("signature", sign(source));
        JsonNode data = send(HttpRequest.newBuilder(API)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))).path("data");
        if (data.isMissingNode() || data.path("paymentLinkId").asText("").isBlank())
            throw new ExternalServiceException("PayOS không trả về link thanh toán hợp lệ");
        return new Link(data.path("checkoutUrl").asText(null), data.path("qrCode").asText(null),
                data.path("paymentLinkId").asText());
    }

    @Override
    public Status getStatus(long orderCode) {
        JsonNode data = send(HttpRequest.newBuilder(URI.create(API + "/" + orderCode)).GET()).path("data");
        if (data.isMissingNode() || data.path("orderCode").asLong(-1) != orderCode)
            throw new ExternalServiceException("PayOS không trả về trạng thái đơn hợp lệ");
        return new Status(data.path("status").asText(""), data.path("amountPaid").asLong(0));
    }

    @Override
    public boolean verifyWebhook(JsonNode body) {
        if (body == null || !body.path("data").isObject()) return false;
        String provided = body.path("signature").asText("");
        if (!provided.matches("[0-9a-fA-F]{64}")) return false;
        Map<String, Object> data = json.convertValue(body.path("data"), Map.class);
        StringBuilder source = new StringBuilder();
        for (var entry : new TreeMap<>(data).entrySet()) {
            if (!source.isEmpty()) source.append('&');
            Object value = entry.getValue();
            source.append(entry.getKey()).append('=').append(value == null || "null".equals(value)
                    || "undefined".equals(value) ? "" : value instanceof Map || value instanceof Iterable
                    ? json.writeValueAsString(value) : value);
        }
        byte[] expected = HexFormat.of().parseHex(sign(source.toString()));
        return MessageDigest.isEqual(expected, HexFormat.of().parseHex(provided.toLowerCase()));
    }

    @Override
    public boolean isFake() { return false; }

    private JsonNode send(HttpRequest.Builder builder) {
        try {
            HttpRequest request = builder.header("x-client-id", clientId).header("x-api-key", apiKey)
                    .timeout(Duration.ofSeconds(15)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2)
                throw new ExternalServiceException("PayOS tạm thời không xử lý được yêu cầu (HTTP "
                        + response.statusCode() + ")");
            JsonNode root = json.readTree(response.body());
            if (!"00".equals(root.path("code").asText()))
                throw new ExternalServiceException("PayOS từ chối yêu cầu thanh toán");
            return root;
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ExternalServiceException("Không kết nối được PayOS", exception);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Không tạo được chữ ký PayOS", exception);
        }
    }
}
