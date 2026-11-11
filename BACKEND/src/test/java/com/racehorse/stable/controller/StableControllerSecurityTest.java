package com.racehorse.stable.controller;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.horse.service.OwnerHorseReadGuard;
import com.racehorse.stable.service.StableService;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = StableControllerSecurityTest.TestConfig.class)
@WebAppConfiguration
class StableControllerSecurityTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        public StableService stableService() {
            return mock(StableService.class);
        }

        @Bean
        public OwnerHorseReadGuard ownerHorseReadGuard() {
            return mock(OwnerHorseReadGuard.class);
        }

        @Bean
        public StableController stableController(StableService service, OwnerHorseReadGuard guard) {
            return new StableController(service, guard);
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
    private StableService service;

    @Autowired
    private OwnerHorseReadGuard ownerGuard;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        reset(service, ownerGuard);
    }

    private RequestPostProcessor mockJwt(String... authorities) {
        org.springframework.security.core.GrantedAuthority[] auths = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toArray(org.springframework.security.core.GrantedAuthority[]::new);
        return jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(auths);
    }

    private String validRoutineJson() {
        return """
                {
                    "activityType": "GROOMING",
                    "startTime": "08:00:00",
                    "endTime": "09:00:00",
                    "notes": "Morning grooming"
                }
                """;
    }

    // --- CREATE (POST) ---

    @Test
    void stableStaffWithPermCanAddDailyRoutine() throws Exception {
        UUID horseId = UUID.randomUUID();
        when(service.addDailyRoutine(eq(horseId), any(UUID.class), eq(true), any()))
                .thenReturn(new StableService.DailyRoutineView(UUID.randomUUID(), horseId, "GROOMING", LocalTime.of(8, 0), LocalTime.of(9, 0), "Morning grooming", true));

        mockMvc.perform(post("/api/stables/horses/" + horseId + "/daily-routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoutineJson())
                        .with(mockJwt("ROLE_STABLE_STAFF", "PERM_STABLE_CARE_EDIT")))
                .andExpect(status().isCreated());

        verify(service).addDailyRoutine(eq(horseId), any(UUID.class), eq(true), any());
    }

    @Test
    void stableStaffWithoutPermCannotAddDailyRoutine() throws Exception {
        UUID horseId = UUID.randomUUID();

        mockMvc.perform(post("/api/stables/horses/" + horseId + "/daily-routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoutineJson())
                        .with(mockJwt("ROLE_STABLE_STAFF", "PERM_STABLE_CARE_VIEW")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void headTrainerCannotAddDailyRoutine() throws Exception {
        UUID horseId = UUID.randomUUID();

        mockMvc.perform(post("/api/stables/horses/" + horseId + "/daily-routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoutineJson())
                        .with(mockJwt("ROLE_HEAD_TRAINER", "PERM_STABLE_CARE_EDIT")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void clubManagerCannotAddDailyRoutine() throws Exception {
        UUID horseId = UUID.randomUUID();

        mockMvc.perform(post("/api/stables/horses/" + horseId + "/daily-routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoutineJson())
                        .with(mockJwt("ROLE_CLUB_MANAGER", "PERM_STABLE_CARE_EDIT")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void veterinarianCannotAddDailyRoutine() throws Exception {
        UUID horseId = UUID.randomUUID();

        mockMvc.perform(post("/api/stables/horses/" + horseId + "/daily-routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoutineJson())
                        .with(mockJwt("ROLE_VETERINARIAN", "PERM_STABLE_CARE_VIEW", "PERM_MEDICAL_EDIT")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void horseOwnerCannotAddDailyRoutine() throws Exception {
        UUID horseId = UUID.randomUUID();

        mockMvc.perform(post("/api/stables/horses/" + horseId + "/daily-routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoutineJson())
                        .with(mockJwt("ROLE_HORSE_OWNER", "PERM_STABLE_CARE_VIEW")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void stableStaffReceives409WhenHorseIsInjuredOrRestricted() throws Exception {
        UUID horseId = UUID.randomUUID();
        when(service.addDailyRoutine(eq(horseId), any(UUID.class), eq(true), any()))
                .thenThrow(new BusinessException("Ngựa đang trong chế độ điều trị chấn thương hoặc có lệnh khóa y tế."));

        mockMvc.perform(post("/api/stables/horses/" + horseId + "/daily-routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoutineJson())
                        .with(mockJwt("ROLE_STABLE_STAFF", "PERM_STABLE_CARE_EDIT")))
                .andExpect(status().isConflict());
    }

    @Test
    void stableStaffReceives409WhenHorseIsCurrentlyTraining() throws Exception {
        UUID horseId = UUID.randomUUID();
        when(service.addDailyRoutine(eq(horseId), any(UUID.class), eq(true), any()))
                .thenThrow(new BusinessException("Ngựa đang trong lịch trình huấn luyện."));

        mockMvc.perform(post("/api/stables/horses/" + horseId + "/daily-routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoutineJson())
                        .with(mockJwt("ROLE_STABLE_STAFF", "PERM_STABLE_CARE_EDIT")))
                .andExpect(status().isConflict());
    }

    @Test
    void multiRoleManagerCannotBypassMutationGuardWhenHorseIsInjured() throws Exception {
        UUID horseId = UUID.randomUUID();
        when(service.addDailyRoutine(eq(horseId), any(UUID.class), eq(true), any()))
                .thenThrow(new BusinessException("Ngựa đang trong chế độ điều trị chấn thương hoặc có lệnh khóa y tế."));

        mockMvc.perform(post("/api/stables/horses/" + horseId + "/daily-routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoutineJson())
                        .with(mockJwt("ROLE_CLUB_MANAGER", "ROLE_STABLE_STAFF", "PERM_STABLE_CARE_EDIT")))
                .andExpect(status().isConflict());

        verify(service).addDailyRoutine(eq(horseId), any(UUID.class), eq(true), any());
    }

    @Test
    void multiRoleTrainerCannotBypassMutationGuardWhenHorseIsTraining() throws Exception {
        UUID horseId = UUID.randomUUID();
        when(service.addDailyRoutine(eq(horseId), any(UUID.class), eq(true), any()))
                .thenThrow(new BusinessException("Ngựa đang trong lịch trình huấn luyện."));

        mockMvc.perform(post("/api/stables/horses/" + horseId + "/daily-routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoutineJson())
                        .with(mockJwt("ROLE_HEAD_TRAINER", "ROLE_STABLE_STAFF", "PERM_STABLE_CARE_EDIT")))
                .andExpect(status().isConflict());

        verify(service).addDailyRoutine(eq(horseId), any(UUID.class), eq(true), any());
    }

    // --- UPDATE (PUT) ---

    @Test
    void stableStaffWithPermCanUpdateDailyRoutine() throws Exception {
        UUID routineId = UUID.randomUUID();
        when(service.updateDailyRoutine(eq(routineId), any(UUID.class), eq(true), any()))
                .thenReturn(new StableService.DailyRoutineView(routineId, UUID.randomUUID(), "GROOMING", LocalTime.of(8, 0), LocalTime.of(9, 0), "Updated grooming", true));

        mockMvc.perform(put("/api/stables/daily-routines/" + routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoutineJson())
                        .with(mockJwt("ROLE_STABLE_STAFF", "PERM_STABLE_CARE_EDIT")))
                .andExpect(status().isOk());

        verify(service).updateDailyRoutine(eq(routineId), any(UUID.class), eq(true), any());
    }

    @Test
    void stableStaffWithoutPermCannotUpdateDailyRoutine() throws Exception {
        UUID routineId = UUID.randomUUID();

        mockMvc.perform(put("/api/stables/daily-routines/" + routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoutineJson())
                        .with(mockJwt("ROLE_STABLE_STAFF", "PERM_STABLE_CARE_VIEW")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void headTrainerCannotUpdateDailyRoutine() throws Exception {
        UUID routineId = UUID.randomUUID();

        mockMvc.perform(put("/api/stables/daily-routines/" + routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoutineJson())
                        .with(mockJwt("ROLE_HEAD_TRAINER", "PERM_STABLE_CARE_EDIT")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void clubManagerCannotUpdateDailyRoutine() throws Exception {
        UUID routineId = UUID.randomUUID();

        mockMvc.perform(put("/api/stables/daily-routines/" + routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoutineJson())
                        .with(mockJwt("ROLE_CLUB_MANAGER", "PERM_STABLE_CARE_EDIT")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void veterinarianCannotUpdateDailyRoutine() throws Exception {
        UUID routineId = UUID.randomUUID();

        mockMvc.perform(put("/api/stables/daily-routines/" + routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoutineJson())
                        .with(mockJwt("ROLE_VETERINARIAN", "PERM_STABLE_CARE_VIEW", "PERM_MEDICAL_EDIT")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void horseOwnerCannotUpdateDailyRoutine() throws Exception {
        UUID routineId = UUID.randomUUID();

        mockMvc.perform(put("/api/stables/daily-routines/" + routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoutineJson())
                        .with(mockJwt("ROLE_HORSE_OWNER", "PERM_STABLE_CARE_VIEW")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    // --- DELETE (REMOVED ENDPOINT PER RH-STABLE-UI-01C.3) ---

    @Test
    void dailyRoutineDeleteEndpointIsNotExposedForAnyRole() throws Exception {
        UUID routineId = UUID.randomUUID();

        // When DELETE is called on /api/stables/daily-routines/{id}, method is not supported
        mockMvc.perform(delete("/api/stables/daily-routines/" + routineId)
                        .with(mockJwt("ROLE_STABLE_STAFF", "PERM_STABLE_CARE_EDIT")))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(sc).isIn(404, 405, 500);
                    if (result.getResolvedException() != null) {
                        org.assertj.core.api.Assertions.assertThat(result.getResolvedException())
                                .isInstanceOf(org.springframework.web.HttpRequestMethodNotSupportedException.class);
                    }
                });

        mockMvc.perform(delete("/api/stables/daily-routines/" + routineId)
                        .with(mockJwt("ROLE_CLUB_MANAGER", "PERM_STABLE_CARE_EDIT")))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(sc).isIn(404, 405, 500);
                });

        mockMvc.perform(delete("/api/stables/daily-routines/" + routineId)
                        .with(mockJwt("ROLE_HEAD_TRAINER", "PERM_STABLE_CARE_EDIT")))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(sc).isIn(404, 405, 500);
                });

        mockMvc.perform(delete("/api/stables/daily-routines/" + routineId)
                        .with(mockJwt("ROLE_HORSE_OWNER", "PERM_STABLE_CARE_VIEW")))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(sc).isIn(403, 404, 405, 500);
                });

        verifyNoInteractions(service);
    }

    // --- READ (GET) ---

    @Test
    void stableStaffCanReadDailyRoutinesWithViewPermission() throws Exception {
        UUID horseId = UUID.randomUUID();

        mockMvc.perform(get("/api/stables/horses/" + horseId + "/daily-routines")
                        .with(mockJwt("ROLE_STABLE_STAFF", "PERM_STABLE_CARE_VIEW")))
                .andExpect(status().isOk());

        verify(service).listDailyRoutines(horseId);
    }

    @Test
    void unauthenticatedUserCannotAccessDailyRoutines() throws Exception {
        UUID horseId = UUID.randomUUID();

        mockMvc.perform(get("/api/stables/horses/" + horseId + "/daily-routines"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(service);
    }

    // --- BACKFILL AUTHORIZATION (Approved 01C.6: ROLE_STABLE_STAFF only) ---

    @Test
    void stableStaffWithPermCanExecuteBackfill() throws Exception {
        when(service.executeRoutineBackfill(any()))
                .thenReturn(new StableService.RoutineBackfillPreview(0, 0, 0, 0, 0, java.util.List.of()));

        mockMvc.perform(post("/api/stables/routines/backfill-execute")
                        .with(mockJwt("ROLE_STABLE_STAFF", "PERM_STABLE_CARE_EDIT")))
                .andExpect(status().isOk());

        verify(service).executeRoutineBackfill(any());
    }

    @Test
    void stableStaffWithoutPermCannotExecuteBackfill() throws Exception {
        mockMvc.perform(post("/api/stables/routines/backfill-execute")
                        .with(mockJwt("ROLE_STABLE_STAFF")))
                .andExpect(status().isForbidden());

        verify(service, never()).executeRoutineBackfill(any());
    }

    @Test
    void headTrainerCannotExecuteBackfill() throws Exception {
        mockMvc.perform(post("/api/stables/routines/backfill-execute")
                        .with(mockJwt("ROLE_HEAD_TRAINER", "PERM_STABLE_CARE_EDIT")))
                .andExpect(status().isForbidden());

        verify(service, never()).executeRoutineBackfill(any());
    }

    @Test
    void clubManagerCannotExecuteBackfill() throws Exception {
        mockMvc.perform(post("/api/stables/routines/backfill-execute")
                        .with(mockJwt("ROLE_CLUB_MANAGER", "PERM_STABLE_CARE_EDIT")))
                .andExpect(status().isForbidden());

        verify(service, never()).executeRoutineBackfill(any());
    }

    @Test
    void veterinarianCannotExecuteBackfill() throws Exception {
        mockMvc.perform(post("/api/stables/routines/backfill-execute")
                        .with(mockJwt("ROLE_VETERINARIAN", "PERM_STABLE_CARE_EDIT")))
                .andExpect(status().isForbidden());

        verify(service, never()).executeRoutineBackfill(any());
    }

    @Test
    void horseOwnerCannotExecuteBackfill() throws Exception {
        mockMvc.perform(post("/api/stables/routines/backfill-execute")
                        .with(mockJwt("ROLE_HORSE_OWNER", "PERM_STABLE_CARE_EDIT")))
                .andExpect(status().isForbidden());

        verify(service, never()).executeRoutineBackfill(any());
    }

    @Test
    void routineBackfillPreviewAccessibleWithViewPermission() throws Exception {
        when(service.previewRoutineBackfill())
                .thenReturn(new StableService.RoutineBackfillPreview(0, 0, 0, 0, 0, java.util.List.of()));

        mockMvc.perform(get("/api/stables/routines/backfill-preview")
                        .with(mockJwt("ROLE_STABLE_STAFF", "PERM_STABLE_CARE_VIEW")))
                .andExpect(status().isOk());

        verify(service).previewRoutineBackfill();
    }
}
