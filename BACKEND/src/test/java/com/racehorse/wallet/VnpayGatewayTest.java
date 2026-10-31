package com.racehorse.wallet;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VnpayGatewayTest {
    private final VnpayGateway gateway = new VnpayGateway(
            "TEST1234", "sandbox-secret-for-test-only-123456",
            "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", "https://example.test/wallet",
            Clock.systemUTC());

    @Test
    void signsSandboxCheckoutAndRejectsTamperedReturnParameters() {
        var link = gateway.create(123456789, 5_000_000, "123456789",
                Instant.now().plusSeconds(900), "127.0.0.1");
        Map<String, String> parameters = new TreeMap<>();
        Arrays.stream(URI.create(link.checkoutUrl()).getRawQuery().split("&"))
                .map(part -> part.split("=", 2))
                .forEach(pair -> parameters.put(
                        URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));

        assertEquals("500000000", parameters.get("vnp_Amount"));
        assertTrue(gateway.verifyVnpay(parameters));

        parameters.put("vnp_Amount", "500000001");
        assertFalse(gateway.verifyVnpay(parameters));
    }
}
