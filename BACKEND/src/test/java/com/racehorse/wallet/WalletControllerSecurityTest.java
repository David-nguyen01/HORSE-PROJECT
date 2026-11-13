package com.racehorse.wallet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = WalletControllerSecurityTest.TestConfig.class)
@WebAppConfiguration
class WalletControllerSecurityTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        public WalletService walletService() {
            return mock(WalletService.class);
        }

        @Bean
        public WalletController walletController(WalletService service) {
            return new WalletController(service);
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private WalletService service;

    private MockMvc mvc;
    private final UUID ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("HORSE_OWNER can retrieve discontinued expiry summary")
    void horseOwner_canRetrieveDiscontinuedExpirySummary() throws Exception {
        when(service.discontinuedExpirySummary(ownerId)).thenReturn(
                new WalletService.DiscontinuedExpirySummaryResponse(true,
                        "Một hoặc nhiều ngựa của bạn có gói dịch vụ đã ngừng cung cấp và đã hết hạn. Vui lòng kiểm tra mục Ví & Dịch vụ để chọn gói mới."));

        mvc.perform(get("/api/wallet/discontinued-expiry-summary")
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_HORSE_OWNER")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasUnresolvedExpiredPackages").value(true))
                .andExpect(jsonPath("$.warningMessage").value("Một hoặc nhiều ngựa của bạn có gói dịch vụ đã ngừng cung cấp và đã hết hạn. Vui lòng kiểm tra mục Ví & Dịch vụ để chọn gói mới."))
                .andExpect(jsonPath("$.horseId").doesNotExist())
                .andExpect(jsonPath("$.horseName").doesNotExist());
    }

    @Test
    @DisplayName("CLUB_MANAGER is denied access to discontinued expiry summary with 403")
    void clubManager_isDeniedWith403() throws Exception {
        mvc.perform(get("/api/wallet/discontinued-expiry-summary")
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLUB_MANAGER")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HEAD_TRAINER is denied access to discontinued expiry summary with 403")
    void headTrainer_isDeniedWith403() throws Exception {
        mvc.perform(get("/api/wallet/discontinued-expiry-summary")
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_HEAD_TRAINER")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("VETERINARIAN is denied access to discontinued expiry summary with 403")
    void veterinarian_isDeniedWith403() throws Exception {
        mvc.perform(get("/api/wallet/discontinued-expiry-summary")
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_VETERINARIAN")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("STABLE_STAFF is denied access to discontinued expiry summary with 403")
    void stableStaff_isDeniedWith403() throws Exception {
        mvc.perform(get("/api/wallet/discontinued-expiry-summary")
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_STABLE_STAFF")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request to discontinued expiry summary receives 401 or 403")
    void unauthenticated_receives401Or403() throws Exception {
        mvc.perform(get("/api/wallet/discontinued-expiry-summary")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("HORSE_OWNER can retrieve wallet view containing targeted alert flags")
    void horseOwner_canRetrieveWalletWithTargetedAlert() throws Exception {
        WalletService.HorsePlanView horsePlan = new WalletService.HorsePlanView(
                UUID.randomUUID(), "H001", "Thunder", "P30", null,
                null, null, null, "UNPAID", "OVERDUE",
                BigDecimal.ZERO, true, "P30"
        );
        when(service.wallet(ownerId)).thenReturn(
                new WalletService.WalletView(BigDecimal.valueOf(1_000_000), List.of(horsePlan), BigDecimal.ZERO, false, true)
        );

        mvc.perform(get("/api/wallet")
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_HORSE_OWNER")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horses[0].hasDiscontinuedExpiryAlert").value(true))
                .andExpect(jsonPath("$.horses[0].discontinuedPackageCode").value("P30"));
    }
}
