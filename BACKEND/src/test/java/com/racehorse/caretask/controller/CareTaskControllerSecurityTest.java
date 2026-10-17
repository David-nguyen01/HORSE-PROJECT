package com.racehorse.caretask.controller;

import com.racehorse.caretask.dto.CareTaskResponse;
import com.racehorse.caretask.service.CareTaskService;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.service.OwnerHorseReadGuard;
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

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CareTaskControllerSecurityTest.TestConfig.class)
@WebAppConfiguration
class CareTaskControllerSecurityTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        public CareTaskService careTaskService() {
            return mock(CareTaskService.class);
        }

        @Bean
        public OwnerHorseReadGuard ownerHorseReadGuard() {
            return mock(OwnerHorseReadGuard.class);
        }

        @Bean
        public CareTaskController careTaskController(CareTaskService service, OwnerHorseReadGuard guard) {
            return new CareTaskController(service, guard);
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
    private CareTaskService service;

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

    @Test
    void unauthenticatedRequestToPhotoReturns4xxClientError() throws Exception {
        UUID taskId = UUID.randomUUID();
        mockMvc.perform(get("/api/care-tasks/" + taskId + "/photo"))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_HORSE_OWNER"})
    void userWithoutStableCareViewAuthorityIsForbidden() throws Exception {
        UUID taskId = UUID.randomUUID();
        mockMvc.perform(get("/api/care-tasks/" + taskId + "/photo"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_STABLE_STAFF", "PERM_STABLE_CARE_VIEW"})
    void authorizedStaffCanRetrievePhotoWithCorrectContentTypeAndBytes() throws Exception {
        UUID taskId = UUID.randomUUID();
        byte[] imageBytes = new byte[]{(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47};
        CareTaskResponse task = mock(CareTaskResponse.class);
        when(task.horseId()).thenReturn(null);
        when(service.getTask(taskId)).thenReturn(task);
        when(service.getTaskPhoto(taskId)).thenReturn(
                new CareTaskService.TaskPhotoData(imageBytes, "image/png", "/api/care-tasks/" + taskId + "/photo", "Máng nước")
        );

        mockMvc.perform(get("/api/care-tasks/" + taskId + "/photo"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(content().bytes(imageBytes));

        verify(service).getTask(taskId);
        verify(service).getTaskPhoto(taskId);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_STABLE_STAFF", "PERM_STABLE_CARE_VIEW"})
    void taskWithoutPhotoReturnsNotFound() throws Exception {
        UUID taskId = UUID.randomUUID();
        CareTaskResponse task = mock(CareTaskResponse.class);
        when(task.horseId()).thenReturn(null);
        when(service.getTask(taskId)).thenReturn(task);
        when(service.getTaskPhoto(taskId)).thenThrow(new ResourceNotFoundException("Nhiệm vụ này không có ảnh đính kèm"));

        mockMvc.perform(get("/api/care-tasks/" + taskId + "/photo"))
                .andExpect(status().isNotFound());
    }
}
