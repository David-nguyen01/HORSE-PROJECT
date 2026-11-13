package com.racehorse.notification.controller;

import com.racehorse.notification.dto.BroadcastDismissalResponse;
import com.racehorse.notification.dto.BroadcastNotificationResponse;
import com.racehorse.notification.entity.NotificationPriority;
import com.racehorse.notification.service.NotificationService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = NotificationBroadcastControllerSecurityTest.TestConfig.class)
@WebAppConfiguration
class NotificationBroadcastControllerSecurityTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        public NotificationService notificationService() {
            return mock(NotificationService.class);
        }

        @Bean
        public NotificationController notificationController(NotificationService service) {
            return new NotificationController(service);
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
    private NotificationService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        reset(service);
    }

    // ==========================================
    // GET /api/notifications/broadcasts
    // ==========================================

    @Test
    @DisplayName("HORSE_OWNER can list broadcasts (200 OK)")
    void ownerCanListBroadcasts() throws Exception {
        UUID ownerId = UUID.randomUUID();
        when(service.listActiveBroadcasts(ownerId)).thenReturn(List.of(
                new BroadcastNotificationResponse(
                        UUID.randomUUID(), "SERVICE_PACKAGE_DISCONTINUED", NotificationPriority.HIGH,
                        "Title", "Message", "SERVICE_PACKAGE", UUID.randomUUID(), Instant.now(), null, false
                )
        ));

        mockMvc.perform(get("/api/notifications/broadcasts")
                        .with(jwt().jwt(b -> b.subject(ownerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_HORSE_OWNER"))))
                .andExpect(status().isOk());

        verify(service).listActiveBroadcasts(ownerId);
    }

    @Test
    @DisplayName("CLUB_MANAGER cannot list broadcasts (403 Forbidden)")
    void clubManagerCannotListBroadcasts() throws Exception {
        UUID managerId = UUID.randomUUID();

        mockMvc.perform(get("/api/notifications/broadcasts")
                        .with(jwt().jwt(b -> b.subject(managerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLUB_MANAGER"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("HEAD_TRAINER cannot list broadcasts (403 Forbidden)")
    void headTrainerCannotListBroadcasts() throws Exception {
        UUID trainerId = UUID.randomUUID();

        mockMvc.perform(get("/api/notifications/broadcasts")
                        .with(jwt().jwt(b -> b.subject(trainerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_HEAD_TRAINER"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("VETERINARIAN cannot list broadcasts (403 Forbidden)")
    void veterinarianCannotListBroadcasts() throws Exception {
        UUID vetId = UUID.randomUUID();

        mockMvc.perform(get("/api/notifications/broadcasts")
                        .with(jwt().jwt(b -> b.subject(vetId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_VETERINARIAN"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("STABLE_STAFF cannot list broadcasts (403 Forbidden)")
    void stableStaffCannotListBroadcasts() throws Exception {
        UUID staffId = UUID.randomUUID();

        mockMvc.perform(get("/api/notifications/broadcasts")
                        .with(jwt().jwt(b -> b.subject(staffId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_STABLE_STAFF"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Unauthenticated cannot list broadcasts (4xx Client Error / Denied)")
    void unauthenticatedCannotListBroadcasts() throws Exception {
        mockMvc.perform(get("/api/notifications/broadcasts"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(service);
    }

    // ==========================================
    // POST /api/notifications/broadcasts/{id}/dismiss
    // ==========================================

    @Test
    @DisplayName("HORSE_OWNER can dismiss broadcast via POST (200 OK)")
    void ownerCanDismissBroadcastPost() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        when(service.dismissBroadcast(ownerId, notifId))
                .thenReturn(new BroadcastDismissalResponse(notifId, true, Instant.now()));

        mockMvc.perform(post("/api/notifications/broadcasts/{notificationId}/dismiss", notifId)
                        .with(jwt().jwt(b -> b.subject(ownerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_HORSE_OWNER"))))
                .andExpect(status().isOk());

        verify(service).dismissBroadcast(ownerId, notifId);
    }

    @Test
    @DisplayName("HORSE_OWNER can dismiss broadcast via PATCH (200 OK)")
    void ownerCanDismissBroadcastPatch() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        when(service.dismissBroadcast(ownerId, notifId))
                .thenReturn(new BroadcastDismissalResponse(notifId, true, Instant.now()));

        mockMvc.perform(patch("/api/notifications/broadcasts/{notificationId}/dismiss", notifId)
                        .with(jwt().jwt(b -> b.subject(ownerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_HORSE_OWNER"))))
                .andExpect(status().isOk());

        verify(service).dismissBroadcast(ownerId, notifId);
    }

    @Test
    @DisplayName("CLUB_MANAGER cannot dismiss broadcast via POST (403 Forbidden)")
    void clubManagerCannotDismissBroadcast() throws Exception {
        UUID managerId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();

        mockMvc.perform(post("/api/notifications/broadcasts/{notificationId}/dismiss", notifId)
                        .with(jwt().jwt(b -> b.subject(managerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLUB_MANAGER"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("HEAD_TRAINER cannot dismiss broadcast via POST (403 Forbidden)")
    void headTrainerCannotDismissBroadcast() throws Exception {
        UUID trainerId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();

        mockMvc.perform(post("/api/notifications/broadcasts/{notificationId}/dismiss", notifId)
                        .with(jwt().jwt(b -> b.subject(trainerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_HEAD_TRAINER"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("VETERINARIAN cannot dismiss broadcast via POST (403 Forbidden)")
    void veterinarianCannotDismissBroadcast() throws Exception {
        UUID vetId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();

        mockMvc.perform(post("/api/notifications/broadcasts/{notificationId}/dismiss", notifId)
                        .with(jwt().jwt(b -> b.subject(vetId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_VETERINARIAN"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("STABLE_STAFF cannot dismiss broadcast via POST (403 Forbidden)")
    void stableStaffCannotDismissBroadcast() throws Exception {
        UUID staffId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();

        mockMvc.perform(post("/api/notifications/broadcasts/{notificationId}/dismiss", notifId)
                        .with(jwt().jwt(b -> b.subject(staffId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_STABLE_STAFF"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("CLUB_MANAGER cannot dismiss broadcast via PATCH (403 Forbidden)")
    void clubManagerCannotDismissBroadcastPatch() throws Exception {
        UUID managerId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();

        mockMvc.perform(patch("/api/notifications/broadcasts/{notificationId}/dismiss", notifId)
                        .with(jwt().jwt(b -> b.subject(managerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLUB_MANAGER"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }
}
