package com.racehorse.wallet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Configuration
public class PayosConfiguration {
    private static final Logger log = LoggerFactory.getLogger(PayosConfiguration.class);

    @Bean
    PayosGateway payosGateway(Environment environment, ObjectMapper json, Clock clock) {
        String mode = environment.getProperty("app.payos.gateway", "vnpay");

        if ("fake".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("Cổng thanh toán mô phỏng (Fake) đã bị loại bỏ. Hệ thống chỉ hỗ trợ cổng thanh toán VNPAY.");
        }

        if ("real".equalsIgnoreCase(mode)) {
            log.warn("Cổng PayOS đang được cấu hình nhưng VNPAY là cổng thanh toán chính thức được chỉ định.");
            return new RealPayosGateway(json,
                    required(environment, "PAYOS_CLIENT_ID"),
                    required(environment, "PAYOS_API_KEY"),
                    required(environment, "PAYOS_CHECKSUM_KEY"),
                    required(environment, "PAYOS_RETURN_URL"),
                    required(environment, "PAYOS_CANCEL_URL"));
        }

        if ("vnpay".equalsIgnoreCase(mode)) {
            String tmnCode = environment.getProperty("VNPAY_TMN_CODE");
            String hashSecret = environment.getProperty("VNPAY_HASH_SECRET");
            String returnUrl = environment.getProperty("VNPAY_RETURN_URL");
            String paymentUrl = environment.getProperty("VNPAY_PAYMENT_URL",
                    "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");

            if (tmnCode == null || tmnCode.isBlank()
                    || hashSecret == null || hashSecret.isBlank()
                    || returnUrl == null || returnUrl.isBlank()) {
                String missingMsg = "Cổng thanh toán VNPAY chưa được kích hoạt: thiếu biến môi trường cấu hình ("
                        + (tmnCode == null || tmnCode.isBlank() ? "VNPAY_TMN_CODE " : "")
                        + (hashSecret == null || hashSecret.isBlank() ? "VNPAY_HASH_SECRET " : "")
                        + (returnUrl == null || returnUrl.isBlank() ? "VNPAY_RETURN_URL" : "")
                        + "). Vui lòng cấu hình đầy đủ để thực hiện nạp tiền.";
                log.warn(missingMsg);
                return new UnconfiguredVnpayGateway(missingMsg.trim());
            }

            return new VnpayGateway(tmnCode.trim(), hashSecret.trim(), paymentUrl.trim(), returnUrl.trim(), clock);
        }

        throw new IllegalStateException("Payment gateway mode không hợp lệ: '" + mode + "'. Chỉ hỗ trợ 'vnpay'.");
    }

    private String required(Environment environment, String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Thiếu biến môi trường " + name);
        return value.trim();
    }
}
