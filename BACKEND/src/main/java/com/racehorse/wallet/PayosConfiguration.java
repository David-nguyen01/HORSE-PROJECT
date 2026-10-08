package com.racehorse.wallet;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;


@Configuration
public class PayosConfiguration {
    @Bean
    PayosGateway payosGateway(Environment environment, ObjectMapper json) {
        String mode = environment.getProperty("app.payos.gateway", "disabled");
        if ("fake".equalsIgnoreCase(mode)) {
            return new FakePayosGateway();
        }
        if ("real".equalsIgnoreCase(mode)) {
            return new RealPayosGateway(json,
                    required(environment, "PAYOS_CLIENT_ID"),
                    required(environment, "PAYOS_API_KEY"),
                    required(environment, "PAYOS_CHECKSUM_KEY"),
                    required(environment, "PAYOS_RETURN_URL"),
                    required(environment, "PAYOS_CANCEL_URL"));
        }
        if (!"disabled".equalsIgnoreCase(mode)) throw new IllegalStateException("PayOS gateway mode không hợp lệ");
        return new DisabledPayosGateway();
    }

    private String required(Environment environment, String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Thiếu biến môi trường " + name);
        return value;
    }
}
