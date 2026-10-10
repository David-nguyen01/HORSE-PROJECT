package com.racehorse.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.racehorse.auth.repository.LoginSessionRepository;
import com.racehorse.auth.security.LoginSessionTokenValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthConfiguration {
    // Fingerprint only: never ship the previous public development key as an active secret.
    private static final String LEGACY_INSECURE_SECRET_SHA256 =
            "39d83034d700174feb1111e317d25fee40613e05ec26ac254e7334c920ee63ba";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecretKey jwtSecretKey(JwtProperties properties) {
        if (properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException("Cần cấu hình JWT_SECRET riêng trước khi khởi động backend");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.secret());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET phải là chuỗi Base64 hợp lệ", exception);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("JWT_SECRET phải chứa ít nhất 32 byte sau khi giải mã Base64");
        }
        try {
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(decoded));
            if (LEGACY_INSECURE_SECRET_SHA256.equals(fingerprint)) {
                throw new IllegalStateException("JWT_SECRET cũ đã công khai; cần thay bằng khóa ngẫu nhiên mới");
            }
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Không có thuật toán SHA-256 để kiểm tra JWT_SECRET", exception);
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey secretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(
            SecretKey secretKey,
            JwtProperties properties,
            LoginSessionRepository sessionRepository,
            Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> validator =
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(properties.issuer()),
                        new LoginSessionTokenValidator(sessionRepository, clock));
        decoder.setJwtValidator(validator);
        return decoder;
    }

}
