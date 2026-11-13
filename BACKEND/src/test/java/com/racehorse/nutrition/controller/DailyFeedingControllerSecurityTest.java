package com.racehorse.nutrition.controller;

import com.racehorse.horse.service.OwnerHorseReadGuard;
import com.racehorse.nutrition.dto.ConfirmMealFeedingRequest;
import com.racehorse.nutrition.dto.DailyFeedingLogResponse;
import com.racehorse.nutrition.service.DailyFeedingService;
import org.junit.jupiter.api.BeforeEach;
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
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DailyFeedingControllerSecurityTest.TestConfig.class)
@WebAppConfiguration
class DailyFeedingControllerSecurityTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        public DailyFeedingService dailyFeedingService() {
            return mock(DailyFeedingService.class);
        }

        @Bean
        public OwnerHorseReadGuard ownerHorseReadGuard() {
            return mock(OwnerHorseReadGuard.class);
        }

        @Bean
        public DailyFeedingController dailyFeedingController(DailyFeedingService service, OwnerHorseReadGuard ownerGuard) {
            return new DailyFeedingController(service, ownerGuard);
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
    private DailyFeedingService service;

    private MockMvc mockMvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        reset(service);
    }

    @Test
    void stableStaffCanConfirmFeeding() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 13);

        DailyFeedingLogResponse sample = new DailyFeedingLogResponse(
                UUID.randomUUID(), horseId, date, "meal_0630_bua_sang_06_30", "Bữa sáng (06:30)",
                LocalTime.of(6, 30), UUID.randomUUID(), 1, "CONFIRMED", userId, "staff_user",
                OffsetDateTime.now(), Map.of(), OffsetDateTime.now()
        );

        when(service.confirmMeal(eq(horseId), any(), any(ConfirmMealFeedingRequest.class))).thenReturn(sample);

        ConfirmMealFeedingRequest req = new ConfirmMealFeedingRequest(date, "meal_0630_bua_sang_06_30", null, null);

        mockMvc.perform(post("/api/horses/" + horseId + "/daily-feeding/confirm")
                        .with(jwt().jwt(j -> j.subject(userId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_STABLE_STAFF"),
                                        new SimpleGrantedAuthority("PERM_STABLE_CARE_EDIT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void veterinarianCannotConfirmFeedingDirectly() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 13);
        ConfirmMealFeedingRequest req = new ConfirmMealFeedingRequest(date, "meal_0630_bua_sang_06_30", null, null);

        mockMvc.perform(post("/api/horses/" + horseId + "/daily-feeding/confirm")
                        .with(jwt().jwt(j -> j.subject(userId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_VETERINARIAN"),
                                        new SimpleGrantedAuthority("PERM_STABLE_CARE_EDIT"),
                                        new SimpleGrantedAuthority("PERM_MEDICAL_EDIT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void headTrainerCannotConfirmFeedingDirectly() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 13);
        ConfirmMealFeedingRequest req = new ConfirmMealFeedingRequest(date, "meal_0630_bua_sang_06_30", null, null);

        mockMvc.perform(post("/api/horses/" + horseId + "/daily-feeding/confirm")
                        .with(jwt().jwt(j -> j.subject(userId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_HEAD_TRAINER"),
                                        new SimpleGrantedAuthority("PERM_STABLE_CARE_EDIT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void horseOwnerCannotConfirmFeeding() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 13);
        ConfirmMealFeedingRequest req = new ConfirmMealFeedingRequest(date, "meal_0630_bua_sang_06_30", null, null);

        mockMvc.perform(post("/api/horses/" + horseId + "/daily-feeding/confirm")
                        .with(jwt().jwt(j -> j.subject(userId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_HORSE_OWNER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotConfirmFeeding() throws Exception {
        UUID horseId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 13);
        ConfirmMealFeedingRequest req = new ConfirmMealFeedingRequest(date, "meal_0630_bua_sang_06_30", null, null);

        mockMvc.perform(post("/api/horses/" + horseId + "/daily-feeding/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void stableStaffWithPermCanAccessHistory() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(service.getHistory(eq(horseId), any(), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/horses/" + horseId + "/daily-feeding/history")
                        .with(jwt().jwt(j -> j.subject(userId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_STABLE_STAFF"),
                                        new SimpleGrantedAuthority("PERM_STABLE_CARE_VIEW"))))
                .andExpect(status().isOk());
    }

    @Test
    void veterinarianWithPermCanAccessHistory() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(service.getHistory(eq(horseId), any(), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/horses/" + horseId + "/daily-feeding/history")
                        .with(jwt().jwt(j -> j.subject(userId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_VETERINARIAN"),
                                        new SimpleGrantedAuthority("PERM_STABLE_CARE_VIEW"))))
                .andExpect(status().isOk());
    }

    @Test
    void horseOwnerDeniedHistoryEvenWithStableCareView() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/horses/" + horseId + "/daily-feeding/history")
                        .with(jwt().jwt(j -> j.subject(userId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_HORSE_OWNER"),
                                        new SimpleGrantedAuthority("PERM_STABLE_CARE_VIEW"))))
                .andExpect(status().isForbidden());

        verify(service, never()).getHistory(any(), any(), any());
    }

    @Test
    void horseOwnerWithoutStableCareViewDeniedHistory() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/horses/" + horseId + "/daily-feeding/history")
                        .with(jwt().jwt(j -> j.subject(userId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_HORSE_OWNER"))))
                .andExpect(status().isForbidden());

        verify(service, never()).getHistory(any(), any(), any());
    }

    @Test
    void headTrainerDeniedHistory() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/horses/" + horseId + "/daily-feeding/history")
                        .with(jwt().jwt(j -> j.subject(userId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_HEAD_TRAINER"),
                                        new SimpleGrantedAuthority("PERM_STABLE_CARE_VIEW"))))
                .andExpect(status().isForbidden());

        verify(service, never()).getHistory(any(), any(), any());
    }

    @Test
    void clubManagerDeniedHistory() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/horses/" + horseId + "/daily-feeding/history")
                        .with(jwt().jwt(j -> j.subject(userId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLUB_MANAGER"),
                                        new SimpleGrantedAuthority("PERM_STABLE_CARE_VIEW"))))
                .andExpect(status().isForbidden());

        verify(service, never()).getHistory(any(), any(), any());
    }

    @Test
    void stableStaffWithoutStableCareViewDeniedHistory() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/horses/" + horseId + "/daily-feeding/history")
                        .with(jwt().jwt(j -> j.subject(userId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_STABLE_STAFF"))))
                .andExpect(status().isForbidden());

        verify(service, never()).getHistory(any(), any(), any());
    }

    @Test
    void unauthenticatedDeniedHistory() throws Exception {
        UUID horseId = UUID.randomUUID();

        mockMvc.perform(get("/api/horses/" + horseId + "/daily-feeding/history"))
                .andExpect(status().is4xxClientError());

        verify(service, never()).getHistory(any(), any(), any());
    }
}
