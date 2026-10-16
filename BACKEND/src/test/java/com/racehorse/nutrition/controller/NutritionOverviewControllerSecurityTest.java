package com.racehorse.nutrition.controller;

import com.racehorse.nutrition.service.NutritionPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = NutritionOverviewControllerSecurityTest.TestConfig.class)
@WebAppConfiguration
class NutritionOverviewControllerSecurityTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        public NutritionPlanService nutritionPlanService() {
            return mock(NutritionPlanService.class);
        }

        @Bean
        public NutritionOverviewController nutritionOverviewController(NutritionPlanService service) {
            return new NutritionOverviewController(service);
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
    private NutritionPlanService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        reset(service);
    }

    // --- Endpoint 1: /api/nutrition/standard-plans ---

    @Test
    void standardPlansUnauthenticatedReturnsUnauthorizedOrForbidden() throws Exception {
        mockMvc.perform(get("/api/nutrition/standard-plans"))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = {"PERM_STABLE_CARE_VIEW", "ROLE_HEAD_TRAINER"})
    void standardPlansAuthorizedUserAllowed() throws Exception {
        when(service.listStandardPlans()).thenReturn(List.of(Map.of("id", "plan-1")));

        mockMvc.perform(get("/api/nutrition/standard-plans"))
                .andExpect(status().isOk());

        verify(service).listStandardPlans();
    }

    @Test
    @WithMockUser(authorities = {"PERM_STABLE_CARE_VIEW", "ROLE_HORSE_OWNER"})
    void standardPlansHorseOwnerDeniedEvenWithAuthority() throws Exception {
        mockMvc.perform(get("/api/nutrition/standard-plans"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_HEAD_TRAINER"})
    void standardPlansUserWithoutStableCareViewDenied() throws Exception {
        mockMvc.perform(get("/api/nutrition/standard-plans"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    // --- Endpoint 2: /api/nutrition/horses-active ---

    @Test
    void horsesActiveUnauthenticatedReturnsClientError() throws Exception {
        mockMvc.perform(get("/api/nutrition/horses-active"))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = {"PERM_STABLE_CARE_VIEW", "ROLE_STABLE_STAFF"})
    void horsesActiveAuthorizedUserAllowed() throws Exception {
        when(service.listHorsesActivePlans(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/nutrition/horses-active?date=2026-10-13"))
                .andExpect(status().isOk());

        verify(service).listHorsesActivePlans(LocalDate.of(2026, 10, 13));
    }

    @Test
    @WithMockUser(authorities = {"PERM_STABLE_CARE_VIEW", "ROLE_HORSE_OWNER"})
    void horsesActiveHorseOwnerDeniedEvenWithAuthority() throws Exception {
        mockMvc.perform(get("/api/nutrition/horses-active?date=2026-10-13"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_STABLE_STAFF"})
    void horsesActiveUserWithoutStableCareViewDenied() throws Exception {
        mockMvc.perform(get("/api/nutrition/horses-active"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    // --- Endpoint 3: /api/nutrition/stables-workload ---

    @Test
    void stablesWorkloadUnauthenticatedReturnsClientError() throws Exception {
        mockMvc.perform(get("/api/nutrition/stables-workload"))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = {"PERM_STABLE_CARE_VIEW", "ROLE_CLUB_MANAGER"})
    void stablesWorkloadAuthorizedUserAllowed() throws Exception {
        when(service.getStableWorkload(any())).thenReturn(Map.of("summary", Map.of()));

        mockMvc.perform(get("/api/nutrition/stables-workload?date=2026-10-13"))
                .andExpect(status().isOk());

        verify(service).getStableWorkload(LocalDate.of(2026, 10, 13));
    }

    @Test
    @WithMockUser(authorities = {"PERM_STABLE_CARE_VIEW", "ROLE_HORSE_OWNER"})
    void stablesWorkloadHorseOwnerDeniedEvenWithAuthority() throws Exception {
        mockMvc.perform(get("/api/nutrition/stables-workload?date=2026-10-13"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_CLUB_MANAGER"})
    void stablesWorkloadUserWithoutStableCareViewDenied() throws Exception {
        mockMvc.perform(get("/api/nutrition/stables-workload"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }
}
