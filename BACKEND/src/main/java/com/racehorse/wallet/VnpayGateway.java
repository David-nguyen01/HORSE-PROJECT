package com.racehorse.wallet;

import com.racehorse.common.exception.ExternalServiceException;
import tools.jackson.databind.JsonNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

final class VnpayGateway implements PayosGateway {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private final String terminalId;
    private final String hashSecret;
    private final String paymentUrl;
    private final String returnUrl;
    private final Clock clock;

    VnpayGateway(String terminalId, String hashSecret, String paymentUrl, String returnUrl, Clock clock) {
        URI uri = URI.create(paymentUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"sandbox.vnpayment.vn".equalsIgnoreCase(uri.getHost())) {
            throw new IllegalStateException("Chỉ hỗ trợ URL VNPAY Sandbox qua HTTPS");
        }
        this.terminalId = terminalId;
        this.hashSecret = hashSecret;
        this.paymentUrl = paymentUrl;
        this.returnUrl = returnUrl;
        this.clock = clock;
    }

    @Override
    public Link create(long orderCode, long amountVnd, String description, Instant expiresAt) {
        return create(orderCode, amountVnd, description, expiresAt, "127.0.0.1");
    }

    @Override
    public Link create(long orderCode, long amountVnd, String description, Instant expiresAt, String ipAddress) {
        TreeMap<String, String> parameters = new TreeMap<>();
        parameters.put("vnp_Version", "2.1.0");
        parameters.put("vnp_Command", "pay");
        parameters.put("vnp_TmnCode", terminalId);
        parameters.put("vnp_Amount", Long.toString(Math.multiplyExact(amountVnd, 100)));
        parameters.put("vnp_CurrCode", "VND");
        parameters.put("vnp_TxnRef", Long.toString(orderCode));
        parameters.put("vnp_OrderInfo", description);
        parameters.put("vnp_OrderType", "other");
        parameters.put("vnp_Locale", "vn");
        parameters.put("vnp_ReturnUrl", returnUrl);
        parameters.put("vnp_IpAddr", ipAddress);
        Instant now = clock.instant();
        parameters.put("vnp_CreateDate", DATE_FORMAT.format(now));
        parameters.put("vnp_ExpireDate", DATE_FORMAT.format(expiresAt));
        String query = encode(parameters);
        return new Link(paymentUrl + "?" + query + "&vnp_SecureHash=" + sign(query), null,
                Long.toString(orderCode));
    }

    @Override
    public Status getStatus(long orderCode) {
        return new Status("PENDING", 0);
    }

    @Override
    public boolean verifyWebhook(JsonNode body) {
        return false;
    }

    @Override
    public boolean verifyVnpay(Map<String, String> parameters) {
        if (parameters == null || !terminalId.equals(parameters.get("vnp_TmnCode"))) return false;
        String provided = parameters.get("vnp_SecureHash");
        if (provided == null || !provided.matches("[0-9a-fA-F]{128}")) return false;
        TreeMap<String, String> signed = new TreeMap<>();
        parameters.forEach((key, value) -> {
            if (key.startsWith("vnp_") && !"vnp_SecureHash".equals(key) && !"vnp_SecureHashType".equals(key))
                signed.put(key, value);
        });
        byte[] expected = HexFormat.of().parseHex(sign(encode(signed)));
        byte[] actual = HexFormat.of().parseHex(provided);
        return MessageDigest.isEqual(expected, actual);
    }

    @Override
    public boolean isFake() { return false; }

    @Override
    public boolean isVnpay() { return true; }

    private String encode(Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new ExternalServiceException("Không tạo được chữ ký VNPAY", exception);
        }
    }
}
