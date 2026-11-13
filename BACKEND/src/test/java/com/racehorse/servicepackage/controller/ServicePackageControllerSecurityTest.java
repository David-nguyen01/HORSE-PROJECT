package com.racehorse.servicepackage.controller;

import com.racehorse.servicepackage.dto.*;
import com.racehorse.servicepackage.service.ServicePackageService;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ServicePackageControllerSecurityTest.TestConfig.class)
@WebAppConfiguration
class ServicePackageControllerSecurityTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        public ServicePackageService servicePackageService() {
            return mock(ServicePackageService.class);
        }

        @Bean
        public ServicePackageController servicePackageController(ServicePackageService service) {
            return new ServicePackageController(service);
        }

        @Bean
        public com.racehorse.common.exception.GlobalExceptionHandler globalExceptionHandler() {
            return new com.racehorse.common.exception.GlobalExceptionHandler();
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .exceptionHandling(e -> e.authenticationEntryPoint(
                            new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED)))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ServicePackageService service;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        reset(service);
    }

    private ServicePackageResponse sampleResponse(UUID id, String code, boolean active) {
        return new ServicePackageResponse(
                id, code, "Gói dịch vụ", "Mô tả",
                new BigDecimal("5000000"), 30, active, 10,
                OffsetDateTime.now(), OffsetDateTime.now(), 0L
        );
    }

    // --- CLUB_MANAGER AUTHORIZATION TESTS ---

    @Test
    @WithMockUser(username = "manager", roles = {"CLUB_MANAGER"})
    void clubManagerCanListActivePackages() throws Exception {
        when(service.listActive()).thenReturn(List.of(sampleResponse(UUID.randomUUID(), "P30", true)));

        mockMvc.perform(get("/api/service-packages/active"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "manager", roles = {"CLUB_MANAGER"})
    void clubManagerCanListReservePackages() throws Exception {
        when(service.listReserve()).thenReturn(List.of(sampleResponse(UUID.randomUUID(), "P365_OLD", false)));

        mockMvc.perform(get("/api/service-packages/reserve"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "manager", roles = {"CLUB_MANAGER"})
    void clubManagerCanCreatePackage() throws Exception {
        CreateServicePackageRequest req = new CreateServicePackageRequest(
                "P180", "Gói 6 tháng", "Mô tả",
                new BigDecimal("28000000"), 180, true, 4);

        when(service.create(any(), any())).thenReturn(sampleResponse(UUID.randomUUID(), "P180", true));

        mockMvc.perform(post("/api/service-packages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "manager", roles = {"CLUB_MANAGER"})
    void clubManagerCanUpdatePackage() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateServicePackageRequest req = new UpdateServicePackageRequest(
                "Gói sửa", "Mô tả", new BigDecimal("30000000"), 180, 4, 0L);

        when(service.update(eq(id), any(), any())).thenReturn(sampleResponse(id, "P180", true));

        mockMvc.perform(put("/api/service-packages/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "manager", roles = {"CLUB_MANAGER"})
    void clubManagerCanMoveToActive() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.transfer(eq(id), any(), eq(true))).thenReturn(sampleResponse(id, "P180", true));

        mockMvc.perform(post("/api/service-packages/" + id + "/move-to-active"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "manager", roles = {"CLUB_MANAGER"})
    void clubManagerCanMoveToReserve() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.transfer(eq(id), any(), eq(false))).thenReturn(sampleResponse(id, "P180", false));

        mockMvc.perform(post("/api/service-packages/" + id + "/move-to-reserve"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "manager", roles = {"CLUB_MANAGER"})
    void clubManagerCanReorderPackages() throws Exception {
        ReorderServicePackagesRequest req = new ReorderServicePackagesRequest(List.of(UUID.randomUUID(), UUID.randomUUID()));
        when(service.reorder(any(), any())).thenReturn(List.of());

        mockMvc.perform(put("/api/service-packages/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "manager", roles = {"CLUB_MANAGER"})
    void clubManagerCanDeletePackage() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(service).delete(eq(id), any());

        mockMvc.perform(delete("/api/service-packages/" + id))
                .andExpect(status().isNoContent());
    }

    // --- HORSE_OWNER RESTRICTION & ACCESS TESTS ---

    @Test
    @WithMockUser(username = "owner", roles = {"HORSE_OWNER"})
    void ownerCanListActivePackages() throws Exception {
        when(service.listActive()).thenReturn(List.of(sampleResponse(UUID.randomUUID(), "P30", true)));

        mockMvc.perform(get("/api/service-packages/active"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "owner", roles = {"HORSE_OWNER"})
    void ownerCanAccessCatalogEndpoint() throws Exception {
        when(service.listActive()).thenReturn(List.of(sampleResponse(UUID.randomUUID(), "P30", true)));

        mockMvc.perform(get("/api/service-packages/catalog"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "owner", roles = {"HORSE_OWNER"})
    void ownerForbiddenFromListingReservePackages() throws Exception {
        mockMvc.perform(get("/api/service-packages/reserve"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner", roles = {"HORSE_OWNER"})
    void ownerForbiddenFromCreatingPackage() throws Exception {
        CreateServicePackageRequest req = new CreateServicePackageRequest(
                "P180", "Gói 6 tháng", "Mô tả",
                new BigDecimal("28000000"), 180, true, 4);

        mockMvc.perform(post("/api/service-packages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner", roles = {"HORSE_OWNER"})
    void ownerForbiddenFromUpdatingPackage() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateServicePackageRequest req = new UpdateServicePackageRequest(
                "Gói sửa", "Mô tả", new BigDecimal("30000000"), 180, 4, 0L);

        mockMvc.perform(put("/api/service-packages/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner", roles = {"HORSE_OWNER"})
    void ownerForbiddenFromMovingPackageStatus() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/service-packages/" + id + "/move-to-reserve"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/service-packages/" + id + "/move-to-active"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner", roles = {"HORSE_OWNER"})
    void ownerForbiddenFromReorderingPackages() throws Exception {
        ReorderServicePackagesRequest req = new ReorderServicePackagesRequest(List.of(UUID.randomUUID()));

        mockMvc.perform(put("/api/service-packages/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner", roles = {"HORSE_OWNER"})
    void ownerForbiddenFromDeletingPackage() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/service-packages/" + id))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner", roles = {"HORSE_OWNER"})
    void ownerForbiddenFromViewingReservePackageDetail() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenReturn(sampleResponse(id, "P_RESERVE", false));

        mockMvc.perform(get("/api/service-packages/" + id))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner", roles = {"HORSE_OWNER"})
    void ownerCanViewActivePackageDetail() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenReturn(sampleResponse(id, "P30", true));

        mockMvc.perform(get("/api/service-packages/" + id))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequestsRejected() throws Exception {
        mockMvc.perform(get("/api/service-packages/active"))
                .andExpect(status().isUnauthorized());
    }
}
