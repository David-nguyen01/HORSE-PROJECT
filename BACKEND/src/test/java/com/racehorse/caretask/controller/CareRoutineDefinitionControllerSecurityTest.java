package com.racehorse.caretask.controller;

import com.racehorse.caretask.dto.*;
import com.racehorse.caretask.service.CareRoutineDefinitionService;
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

import java.time.LocalTime;
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
@ContextConfiguration(classes = CareRoutineDefinitionControllerSecurityTest.TestConfig.class)
@WebAppConfiguration
class CareRoutineDefinitionControllerSecurityTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        public CareRoutineDefinitionService careRoutineDefinitionService() {
            return mock(CareRoutineDefinitionService.class);
        }

        @Bean
        public CareRoutineDefinitionController careRoutineDefinitionController(CareRoutineDefinitionService service) {
            return new CareRoutineDefinitionController(service);
        }

        @Bean
        public com.racehorse.common.exception.GlobalExceptionHandler globalExceptionHandler() {
            return new com.racehorse.common.exception.GlobalExceptionHandler();
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
    private CareRoutineDefinitionService service;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        reset(service);
    }

    @Test
    void unauthenticatedRequestToActiveListReturns4xx() throws Exception {
        mockMvc.perform(get("/api/care-routine-definitions/active"))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_CLUB_MANAGER", "PERM_STABLE_CARE_VIEW", "PERM_STABLE_CARE_EDIT"})
    void clubManagerCanAccessActiveAndReserveLists() throws Exception {
        when(service.listActive()).thenReturn(List.of());
        when(service.listReserve()).thenReturn(List.of());

        mockMvc.perform(get("/api/care-routine-definitions/active"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/care-routine-definitions/reserve"))
                .andExpect(status().isOk());

        verify(service).listActive();
        verify(service).listReserve();
    }

    @Test
    @WithMockUser(authorities = {"ROLE_CLUB_MANAGER", "PERM_STABLE_CARE_EDIT"})
    void clubManagerCanCreateDefinitionInReserve() throws Exception {
        UUID defId = UUID.randomUUID();
        CreateCareRoutineDefinitionRequest req = new CreateCareRoutineDefinitionRequest(
                "ROUTINE_NEW", "Tiêu đề mới", "Mô tả", "CLEANING", LocalTime.of(8, 0), "FACILITY", 10, false
        );
        CareRoutineDefinitionResponse resp = new CareRoutineDefinitionResponse(
                defId, "ROUTINE_NEW", "Tiêu đề mới", "Mô tả", "CLEANING", LocalTime.of(8, 0),
                "FACILITY", false, 10, OffsetDateTime.now(), OffsetDateTime.now()
        );
        when(service.createInReserve(any(), any())).thenReturn(resp);

        mockMvc.perform(post("/api/care-routine-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        verify(service).createInReserve(any(), any());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_CLUB_MANAGER", "PERM_STABLE_CARE_EDIT"})
    void clubManagerCanPerformMutations() throws Exception {
        UUID defId = UUID.randomUUID();
        UpdateCareRoutineDefinitionRequest updateReq = new UpdateCareRoutineDefinitionRequest(
                "Tiêu đề sửa", "Mô tả", "FEEDING", LocalTime.of(9, 0), "FACILITY", 20
        );

        mockMvc.perform(put("/api/care-routine-definitions/" + defId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());
        verify(service).update(eq(defId), any(), any());

        mockMvc.perform(post("/api/care-routine-definitions/" + defId + "/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": true}"))
                .andExpect(status().isOk());
        verify(service).transfer(eq(defId), any(), eq(true));

        mockMvc.perform(post("/api/care-routine-definitions/" + defId + "/move-to-active"))
                .andExpect(status().isOk());
        verify(service, times(2)).transfer(eq(defId), any(), eq(true));

        mockMvc.perform(post("/api/care-routine-definitions/" + defId + "/move-to-reserve"))
                .andExpect(status().isOk());
        verify(service).transfer(eq(defId), any(), eq(false));

        mockMvc.perform(put("/api/care-routine-definitions/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new ReorderCareRoutinesRequest(List.of(defId)))))
                .andExpect(status().isOk());
        verify(service).reorder(any(), any());

        mockMvc.perform(delete("/api/care-routine-definitions/" + defId))
                .andExpect(status().isNoContent());
        verify(service).delete(eq(defId), any());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_STABLE_STAFF", "PERM_STABLE_CARE_VIEW", "PERM_STABLE_CARE_EDIT"})
    void stableStaffCanReadActiveList() throws Exception {
        when(service.listActive()).thenReturn(List.of());

        mockMvc.perform(get("/api/care-routine-definitions/active"))
                .andExpect(status().isOk());

        verify(service).listActive();
    }

    @Test
    @WithMockUser(authorities = {"ROLE_STABLE_STAFF", "PERM_STABLE_CARE_VIEW", "PERM_STABLE_CARE_EDIT"})
    void stableStaffCannotAccessReserveListForbidden() throws Exception {
        mockMvc.perform(get("/api/care-routine-definitions/reserve"))
                .andExpect(status().isForbidden());

        verify(service, never()).listReserve();
    }

    @Test
    @WithMockUser(authorities = {"ROLE_STABLE_STAFF", "PERM_STABLE_CARE_VIEW", "PERM_STABLE_CARE_EDIT"})
    void stableStaffCannotMutateDefinitionsForbidden() throws Exception {
        UUID defId = UUID.randomUUID();
        CreateCareRoutineDefinitionRequest req = new CreateCareRoutineDefinitionRequest(
                "ROUTINE_STAFF", "Thử tạo bởi Staff", "Mô tả", "CLEANING", LocalTime.of(8, 0), "FACILITY", 10, false
        );

        // POST
        mockMvc.perform(post("/api/care-routine-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // PUT
        mockMvc.perform(put("/api/care-routine-definitions/" + defId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // TRANSFER
        mockMvc.perform(post("/api/care-routine-definitions/" + defId + "/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": true}"))
                .andExpect(status().isForbidden());

        // MOVE-TO-ACTIVE
        mockMvc.perform(post("/api/care-routine-definitions/" + defId + "/move-to-active"))
                .andExpect(status().isForbidden());

        // MOVE-TO-RESERVE
        mockMvc.perform(post("/api/care-routine-definitions/" + defId + "/move-to-reserve"))
                .andExpect(status().isForbidden());

        // REORDER
        mockMvc.perform(put("/api/care-routine-definitions/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new ReorderCareRoutinesRequest(List.of(defId)))))
                .andExpect(status().isForbidden());

        // DELETE
        mockMvc.perform(delete("/api/care-routine-definitions/" + defId))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_STABLE_STAFF", "PERM_STABLE_CARE_VIEW"})
    void stableStaffCanReadActiveDefinitionByIdButDeniedReserveDefinition() throws Exception {
        UUID activeId = UUID.randomUUID();
        UUID reserveId = UUID.randomUUID();

        CareRoutineDefinitionResponse activeDef = new CareRoutineDefinitionResponse(
                activeId, "ROUTINE_ACT", "Active", "Desc", "TYPE", LocalTime.of(6, 0), "FACILITY", true, 10,
                OffsetDateTime.now(), OffsetDateTime.now()
        );
        CareRoutineDefinitionResponse reserveDef = new CareRoutineDefinitionResponse(
                reserveId, "ROUTINE_RES", "Reserve", "Desc", "TYPE", LocalTime.of(6, 0), "FACILITY", false, 20,
                OffsetDateTime.now(), OffsetDateTime.now()
        );

        when(service.get(activeId)).thenReturn(activeDef);
        when(service.get(reserveId)).thenReturn(reserveDef);

        // Active definition can be accessed
        mockMvc.perform(get("/api/care-routine-definitions/" + activeId))
                .andExpect(status().isOk());

        // Reserve definition is Forbidden to non-Club-Manager
        mockMvc.perform(get("/api/care-routine-definitions/" + reserveId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_HORSE_OWNER"})
    void horseOwnerDeniedAllEndpointsForbidden() throws Exception {
        UUID defId = UUID.randomUUID();

        mockMvc.perform(get("/api/care-routine-definitions/active"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/care-routine-definitions/reserve"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/care-routine-definitions/" + defId))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/care-routine-definitions/" + defId))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_HEAD_TRAINER", "PERM_TRAINING_VIEW", "PERM_TRAINING_EDIT"})
    void headTrainerDeniedAllCatalogEndpointsForbidden() throws Exception {
        UUID defId = UUID.randomUUID();

        mockMvc.perform(get("/api/care-routine-definitions/active"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/care-routine-definitions/reserve"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/care-routine-definitions/" + defId))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }
}
