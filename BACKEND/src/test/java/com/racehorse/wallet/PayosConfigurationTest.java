package com.racehorse.wallet;

import com.racehorse.common.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PayosConfigurationTest {
    private final PayosConfiguration configuration = new PayosConfiguration();
    private final ObjectMapper json = new ObjectMapper();
    private final Clock clock = Clock.systemUTC();

    @Test
    void rejectsFakeGatewayModeExplicitly() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.payos.gateway", "fake");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> configuration.payosGateway(env, json, clock));
        assertTrue(ex.getMessage().contains("Fake"));
    }

    @Test
    void defaultsToVnpayAndReturnsUnconfiguredWhenCredentialsMissing() {
        MockEnvironment env = new MockEnvironment();
        // No app.payos.gateway set, defaults to vnpay

        PayosGateway gateway = configuration.payosGateway(env, json, clock);
        assertNotNull(gateway);
        assertFalse(gateway.isVnpay());
        assertFalse(gateway.isFake());

        ExternalServiceException ex = assertThrows(ExternalServiceException.class,
                () -> gateway.create(123456, 100000, "Test", Instant.now()));
        assertTrue(ex.getMessage().contains("VNPAY"));
    }

    @Test
    void instantiatesVnpayGatewayWhenCredentialsProvided() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.payos.gateway", "vnpay");
        env.setProperty("VNPAY_TMN_CODE", "SANDBOX01");
        env.setProperty("VNPAY_HASH_SECRET", "secret1234567890abcdef");
        env.setProperty("VNPAY_RETURN_URL", "http://localhost:5173/wallet");

        PayosGateway gateway = configuration.payosGateway(env, json, clock);
        assertNotNull(gateway);
        assertTrue(gateway.isVnpay());
        assertFalse(gateway.isFake());
    }

    @Test
    void rejectsUnknownGatewayModes() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.payos.gateway", "unknown_gateway");

        assertThrows(IllegalStateException.class,
                () -> configuration.payosGateway(env, json, clock));
    }
}
