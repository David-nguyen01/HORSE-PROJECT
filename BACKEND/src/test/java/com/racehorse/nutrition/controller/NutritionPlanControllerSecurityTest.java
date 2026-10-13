package com.racehorse.nutrition.controller;

import com.racehorse.horse.service.OwnerHorseReadGuard;
import com.racehorse.nutrition.dto.ApplyFeedingTemplateRequest;
import com.racehorse.nutrition.dto.CreateNutritionPlanRequest;
import com.racehorse.nutrition.dto.NutritionPlanResponse;
import com.racehorse.nutrition.service.NutritionPlanService;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.context.support.WithMockUser;
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
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
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
@ContextConfiguration(classes = NutritionPlanControllerSecurityTest.TestConfig.class)
@WebAppConfiguration
class NutritionPlanControllerSecurityTest {

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
        public OwnerHorseReadGuard ownerHorseReadGuard() {
            return mock(OwnerHorseReadGuard.class);
        }

        @Bean
        public NutritionPlanController nutritionPlanController(NutritionPlanService service, OwnerHorseReadGuard ownerGuard) {
            return new NutritionPlanController(service, ownerGuard);
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
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        reset(service);
    }

    @Test
    void stableStaffCanApplyStandardTemplate() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(service.applyStandardTemplate(eq(horseId), any(UUID.class), any(ApplyFeedingTemplateRequest.class)))
                .thenReturn(new NutritionPlanResponse(
                        UUID.randomUUID(), horseId, 1, LocalDate.now(), "ACTIVE",
                        "Applied template", Collections.emptyList(), userId, OffsetDateTime.now()
                ));

        ApplyFeedingTemplateRequest req = new ApplyFeedingTemplateRequest("STD_STANDARD", LocalDate.now(), "Test note", false);

        mockMvc.perform(post("/api/horses/{horseId}/nutrition-plan/apply-template", horseId)
                        .with(jwt().jwt(b -> b.subject(userId.toString()))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_STABLE_STAFF"),
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("PERM_STABLE_CARE_EDIT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void stableStaffCannotCallCreatePlanArbitrarily() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        com.racehorse.nutrition.dto.MealItem meal = new com.racehorse.nutrition.dto.MealItem(
                "Bữa sáng", java.time.LocalTime.of(7, 0), new java.math.BigDecimal("1.5"), "Oats",
                new java.math.BigDecimal("3.0"), "Alfalfa", null);
        CreateNutritionPlanRequest req = new CreateNutritionPlanRequest(
                LocalDate.now(), "Arbitrary diet", List.of(meal)
        );

        mockMvc.perform(post("/api/horses/{horseId}/nutrition-plan", horseId)
                        .with(jwt().jwt(b -> b.subject(userId.toString()))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_STABLE_STAFF"),
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("PERM_STABLE_CARE_EDIT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void stableStaffCanViewTemplateOptions() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(service.getTemplateOptionsForHorse(horseId)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/horses/{horseId}/nutrition-plan/template-options", horseId)
                        .with(jwt().jwt(b -> b.subject(userId.toString()))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_STABLE_STAFF"),
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("PERM_STABLE_CARE_VIEW"))))
                .andExpect(status().isOk());
    }

    @Test
    void userWithoutEditPermissionCannotApplyTemplate() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ApplyFeedingTemplateRequest req = new ApplyFeedingTemplateRequest("STD_STANDARD", LocalDate.now(), null, false);

        mockMvc.perform(post("/api/horses/{horseId}/nutrition-plan/apply-template", horseId)
                        .with(jwt().jwt(b -> b.subject(userId.toString()))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_HORSE_OWNER"),
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("PERM_STABLE_CARE_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
