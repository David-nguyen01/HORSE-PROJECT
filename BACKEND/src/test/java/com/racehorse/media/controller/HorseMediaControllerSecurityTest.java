package com.racehorse.media.controller;

import com.racehorse.auth.entity.Role;
import com.racehorse.horse.service.HorseService;
import com.racehorse.media.dto.HorsePhotoResponse;
import com.racehorse.media.dto.HorseProfileFileResponse;
import com.racehorse.media.entity.ProfileFileStatus;
import com.racehorse.media.service.HorsePhotoService;
import com.racehorse.media.service.HorseProfileFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = HorseMediaControllerSecurityTest.TestConfig.class)
@WebAppConfiguration
class HorseMediaControllerSecurityTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        public HorseProfileFileService horseProfileFileService() {
            return mock(HorseProfileFileService.class);
        }

        @Bean
        public HorseService horseService() {
            return mock(HorseService.class);
        }

        @Bean
        public HorsePhotoService horsePhotoService() {
            return mock(HorsePhotoService.class);
        }

        @Bean
        public HorseProfileFileController horseProfileFileController(
                HorseProfileFileService profileFileService,
                HorseService horseService) {
            return new HorseProfileFileController(profileFileService, horseService);
        }

        @Bean
        public HorsePhotoController horsePhotoController(HorsePhotoService photoService) {
            return new HorsePhotoController(photoService);
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
    private HorseProfileFileService profileFileService;

    @Autowired
    private HorseService horseService;

    @Autowired
    private HorsePhotoService photoService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        reset(profileFileService, horseService, photoService);
    }

    private RequestPostProcessor mockJwt(String... authorities) {
        org.springframework.security.core.GrantedAuthority[] auths = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toArray(org.springframework.security.core.GrantedAuthority[]::new);
        return jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())).authorities(auths);
    }

    private MockMultipartFile sampleFile() {
        return new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3});
    }

    private HorseProfileFileResponse sampleProfileResponse(UUID horseId) {
        return new HorseProfileFileResponse(
                UUID.randomUUID(), horseId, null, null, "avatar.png", "image/png", "png", 3,
                "https://res.cloudinary.com/example/image/upload/avatar.png",
                ProfileFileStatus.ACTIVE, Instant.now(), Instant.now());
    }

    private HorsePhotoResponse samplePhotoResponse(UUID horseId) {
        return new HorsePhotoResponse(
                UUID.randomUUID(), horseId, UUID.randomUUID(),
                "https://res.cloudinary.com/example/image/upload/photo.png",
                "photo.png", "image/png", 3, 1, "Gallery Photo", Instant.now());
    }

    // =========================================================================
    // 1. REFLECTION-BASED PREAUTHORIZE CONTRACT VERIFICATION
    // =========================================================================

    @Test
    void horseProfileFileControllerPreAuthorizeExpressionsEnforceClubManagerOnlyForMutations() throws Exception {
        Method replaceMethod = HorseProfileFileController.class.getDeclaredMethod(
                "replace", UUID.class, Jwt.class, MultipartFile.class);
        assertThat(replaceMethod.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("(hasAuthority('PERM_PROFILE_EDIT') and hasRole('CLUB_MANAGER')) or (hasAuthority('PERM_PROFILE_VIEW') and hasRole('HORSE_OWNER'))");

        Method deleteMethod = HorseProfileFileController.class.getDeclaredMethod("delete", UUID.class);
        assertThat(deleteMethod.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('PERM_PROFILE_EDIT') and hasRole('CLUB_MANAGER')");

        Method pendingProposalsMethod = HorseProfileFileController.class.getDeclaredMethod(
                "pendingProposals", UUID.class);
        assertThat(pendingProposalsMethod.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('PERM_PROFILE_APPROVE') and hasRole('CLUB_MANAGER')");

        Method approveMethod = HorseProfileFileController.class.getDeclaredMethod(
                "approve", UUID.class, UUID.class, Jwt.class);
        assertThat(approveMethod.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('PERM_PROFILE_EDIT') and hasRole('CLUB_MANAGER')");

        Method rejectMethod = HorseProfileFileController.class.getDeclaredMethod(
                "reject", UUID.class, UUID.class, Jwt.class);
        assertThat(rejectMethod.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('PERM_PROFILE_EDIT') and hasRole('CLUB_MANAGER')");
    }

    @Test
    void horsePhotoControllerPreAuthorizeExpressionsEnforceClubManagerOnlyForMutations() throws Exception {
        Method uploadMethod = HorsePhotoController.class.getDeclaredMethod(
                "upload", UUID.class, Jwt.class, MultipartFile.class, Integer.class, String.class);
        assertThat(uploadMethod.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('PERM_PROFILE_EDIT') and hasRole('CLUB_MANAGER')");

        Method deleteMethod = HorsePhotoController.class.getDeclaredMethod(
                "delete", UUID.class, UUID.class, Jwt.class);
        assertThat(deleteMethod.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('PERM_PROFILE_EDIT') and hasRole('CLUB_MANAGER')");
    }

    // =========================================================================
    // 2. HEAD_TRAINER DENIAL (HTTP 403 FORBIDDEN)
    // =========================================================================

    @Test
    void headTrainerCannotReplaceMainProfileFile() throws Exception {
        UUID horseId = UUID.randomUUID();
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/horses/" + horseId + "/main-file");
        builder.with(request -> { request.setMethod(HttpMethod.PUT.name()); return request; });

        mockMvc.perform(builder
                        .file(sampleFile())
                        .with(mockJwt("ROLE_HEAD_TRAINER", "PERM_PROFILE_EDIT", "PERM_PROFILE_VIEW")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(profileFileService);
    }

    @Test
    void headTrainerCannotDeleteMainProfileFile() throws Exception {
        UUID horseId = UUID.randomUUID();

        mockMvc.perform(delete("/api/horses/" + horseId + "/main-file")
                        .with(mockJwt("ROLE_HEAD_TRAINER", "PERM_PROFILE_EDIT")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(profileFileService);
    }

    @Test
    void headTrainerCannotViewProposalQueue() throws Exception {
        UUID horseId = UUID.randomUUID();

        mockMvc.perform(get("/api/horses/" + horseId + "/main-file/proposals")
                        .with(mockJwt("ROLE_HEAD_TRAINER", "PERM_PROFILE_APPROVE")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(profileFileService);
    }

    @Test
    void headTrainerCannotApproveProposal() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();

        mockMvc.perform(post("/api/horses/" + horseId + "/main-file/proposals/" + proposalId + "/approve")
                        .with(mockJwt("ROLE_HEAD_TRAINER", "PERM_PROFILE_EDIT")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(profileFileService);
    }

    @Test
    void headTrainerCannotRejectProposal() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();

        mockMvc.perform(post("/api/horses/" + horseId + "/main-file/proposals/" + proposalId + "/reject")
                        .with(mockJwt("ROLE_HEAD_TRAINER", "PERM_PROFILE_EDIT")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(profileFileService);
    }

    @Test
    void headTrainerCannotUploadGalleryPhoto() throws Exception {
        UUID horseId = UUID.randomUUID();

        mockMvc.perform(multipart("/api/horses/" + horseId + "/photos")
                        .file(sampleFile())
                        .with(mockJwt("ROLE_HEAD_TRAINER", "PERM_PROFILE_EDIT")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(photoService);
    }

    @Test
    void headTrainerCannotDeleteGalleryPhoto() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();

        mockMvc.perform(delete("/api/horses/" + horseId + "/photos/" + photoId)
                        .with(mockJwt("ROLE_HEAD_TRAINER", "PERM_PROFILE_EDIT")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(photoService);
    }

    // =========================================================================
    // 3. HEAD_TRAINER READ ACCESS (HTTP 200 OK)
    // =========================================================================

    @Test
    void headTrainerCanGetActiveMainProfileFile() throws Exception {
        UUID horseId = UUID.randomUUID();
        when(profileFileService.getActive(horseId)).thenReturn(sampleProfileResponse(horseId));

        mockMvc.perform(get("/api/horses/" + horseId + "/main-file")
                        .with(mockJwt("ROLE_HEAD_TRAINER", "PERM_PROFILE_VIEW")))
                .andExpect(status().isOk());

        verify(horseService).getByIdForUser(eq(horseId), any(UUID.class), eq(Role.HEAD_TRAINER));
        verify(profileFileService).getActive(horseId);
    }

    @Test
    void headTrainerCanListGalleryPhotos() throws Exception {
        UUID horseId = UUID.randomUUID();
        when(photoService.list(eq(horseId), any(UUID.class), eq(true)))
                .thenReturn(List.of(samplePhotoResponse(horseId)));

        mockMvc.perform(get("/api/horses/" + horseId + "/photos")
                        .with(mockJwt("ROLE_HEAD_TRAINER", "PERM_PROFILE_VIEW")))
                .andExpect(status().isOk());

        verify(photoService).list(eq(horseId), any(UUID.class), eq(true));
    }

    // =========================================================================
    // 4. CLUB_MANAGER FULL ADMINISTRATIVE ACCESS
    // =========================================================================

    @Test
    void clubManagerCanReplaceMainProfileFile() throws Exception {
        UUID horseId = UUID.randomUUID();
        when(profileFileService.replaceDirectly(eq(horseId), any(UUID.class), any()))
                .thenReturn(sampleProfileResponse(horseId));

        MockMultipartHttpServletRequestBuilder builder = multipart("/api/horses/" + horseId + "/main-file");
        builder.with(request -> { request.setMethod(HttpMethod.PUT.name()); return request; });

        mockMvc.perform(builder
                        .file(sampleFile())
                        .with(mockJwt("ROLE_CLUB_MANAGER", "PERM_PROFILE_EDIT")))
                .andExpect(status().isOk());

        verify(profileFileService).replaceDirectly(eq(horseId), any(UUID.class), any());
    }

    @Test
    void clubManagerCanDeleteMainProfileFile() throws Exception {
        UUID horseId = UUID.randomUUID();

        mockMvc.perform(delete("/api/horses/" + horseId + "/main-file")
                        .with(mockJwt("ROLE_CLUB_MANAGER", "PERM_PROFILE_EDIT")))
                .andExpect(status().isNoContent());

        verify(profileFileService).deleteActive(horseId);
    }

    @Test
    void clubManagerCanViewProposalQueue() throws Exception {
        UUID horseId = UUID.randomUUID();
        when(profileFileService.pendingProposals(horseId)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/horses/" + horseId + "/main-file/proposals")
                        .with(mockJwt("ROLE_CLUB_MANAGER", "PERM_PROFILE_APPROVE")))
                .andExpect(status().isOk());

        verify(profileFileService).pendingProposals(horseId);
    }

    @Test
    void clubManagerCanApproveProposal() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        when(profileFileService.approve(eq(horseId), eq(proposalId), any(UUID.class)))
                .thenReturn(sampleProfileResponse(horseId));

        mockMvc.perform(post("/api/horses/" + horseId + "/main-file/proposals/" + proposalId + "/approve")
                        .with(mockJwt("ROLE_CLUB_MANAGER", "PERM_PROFILE_EDIT")))
                .andExpect(status().isOk());

        verify(profileFileService).approve(eq(horseId), eq(proposalId), any(UUID.class));
    }

    @Test
    void clubManagerCanRejectProposal() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();

        mockMvc.perform(post("/api/horses/" + horseId + "/main-file/proposals/" + proposalId + "/reject")
                        .with(mockJwt("ROLE_CLUB_MANAGER", "PERM_PROFILE_EDIT")))
                .andExpect(status().isNoContent());

        verify(profileFileService).reject(eq(horseId), eq(proposalId), any(UUID.class));
    }

    @Test
    void clubManagerCanUploadGalleryPhoto() throws Exception {
        UUID horseId = UUID.randomUUID();
        when(photoService.upload(eq(horseId), any(UUID.class), any(), any(), any()))
                .thenReturn(samplePhotoResponse(horseId));

        mockMvc.perform(multipart("/api/horses/" + horseId + "/photos")
                        .file(sampleFile())
                        .with(mockJwt("ROLE_CLUB_MANAGER", "PERM_PROFILE_EDIT")))
                .andExpect(status().isCreated());

        verify(photoService).upload(eq(horseId), any(UUID.class), any(), any(), any());
    }

    @Test
    void clubManagerCanDeleteGalleryPhoto() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();

        mockMvc.perform(delete("/api/horses/" + horseId + "/photos/" + photoId)
                        .with(mockJwt("ROLE_CLUB_MANAGER", "PERM_PROFILE_EDIT")))
                .andExpect(status().isNoContent());

        verify(photoService).delete(eq(horseId), eq(photoId), any(UUID.class));
    }

    // =========================================================================
    // 5. HORSE_OWNER ACCESS AND RESTRICTIONS
    // =========================================================================

    @Test
    void horseOwnerCanReplaceOwnMainProfileFile() throws Exception {
        UUID horseId = UUID.randomUUID();
        when(profileFileService.replaceDirectly(eq(horseId), any(UUID.class), any()))
                .thenReturn(sampleProfileResponse(horseId));

        MockMultipartHttpServletRequestBuilder builder = multipart("/api/horses/" + horseId + "/main-file");
        builder.with(request -> { request.setMethod(HttpMethod.PUT.name()); return request; });

        mockMvc.perform(builder
                        .file(sampleFile())
                        .with(mockJwt("ROLE_HORSE_OWNER", "PERM_PROFILE_VIEW")))
                .andExpect(status().isOk());

        verify(profileFileService).replaceDirectly(eq(horseId), any(UUID.class), any());
    }

    @Test
    void horseOwnerCanProposeMainProfileFile() throws Exception {
        UUID horseId = UUID.randomUUID();
        when(profileFileService.propose(eq(horseId), any(UUID.class), any()))
                .thenReturn(sampleProfileResponse(horseId));

        mockMvc.perform(multipart("/api/horses/" + horseId + "/main-file/proposals")
                        .file(sampleFile())
                        .with(mockJwt("ROLE_HORSE_OWNER", "PERM_PROFILE_VIEW")))
                .andExpect(status().isCreated());

        verify(profileFileService).propose(eq(horseId), any(UUID.class), any());
    }

    @Test
    void horseOwnerCannotDeleteMainProfileFile() throws Exception {
        UUID horseId = UUID.randomUUID();

        mockMvc.perform(delete("/api/horses/" + horseId + "/main-file")
                        .with(mockJwt("ROLE_HORSE_OWNER", "PERM_PROFILE_VIEW")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(profileFileService);
    }

    @Test
    void horseOwnerCannotViewProposalQueue() throws Exception {
        UUID horseId = UUID.randomUUID();

        mockMvc.perform(get("/api/horses/" + horseId + "/main-file/proposals")
                        .with(mockJwt("ROLE_HORSE_OWNER", "PERM_PROFILE_VIEW")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(profileFileService);
    }

    @Test
    void horseOwnerCannotUploadGalleryPhoto() throws Exception {
        UUID horseId = UUID.randomUUID();

        mockMvc.perform(multipart("/api/horses/" + horseId + "/photos")
                        .file(sampleFile())
                        .with(mockJwt("ROLE_HORSE_OWNER", "PERM_PROFILE_VIEW")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(photoService);
    }

    // =========================================================================
    // 6. CLUB_MANAGER + HORSE_OWNER (01E MULTI-ROLE PRESERVATION)
    // =========================================================================

    @Test
    void multiRoleClubManagerOwnerCanReplaceMainProfileFile() throws Exception {
        UUID horseId = UUID.randomUUID();
        when(profileFileService.replaceDirectly(eq(horseId), any(UUID.class), any()))
                .thenReturn(sampleProfileResponse(horseId));

        MockMultipartHttpServletRequestBuilder builder = multipart("/api/horses/" + horseId + "/main-file");
        builder.with(request -> { request.setMethod(HttpMethod.PUT.name()); return request; });

        mockMvc.perform(builder
                        .file(sampleFile())
                        .with(mockJwt("ROLE_CLUB_MANAGER", "ROLE_HORSE_OWNER", "PERM_PROFILE_EDIT", "PERM_PROFILE_VIEW")))
                .andExpect(status().isOk());

        verify(profileFileService).replaceDirectly(eq(horseId), any(UUID.class), any());
    }

    @Test
    void multiRoleClubManagerOwnerCanUploadGalleryPhoto() throws Exception {
        UUID horseId = UUID.randomUUID();
        when(photoService.upload(eq(horseId), any(UUID.class), any(), any(), any()))
                .thenReturn(samplePhotoResponse(horseId));

        mockMvc.perform(multipart("/api/horses/" + horseId + "/photos")
                        .file(sampleFile())
                        .with(mockJwt("ROLE_CLUB_MANAGER", "ROLE_HORSE_OWNER", "PERM_PROFILE_EDIT", "PERM_PROFILE_VIEW")))
                .andExpect(status().isCreated());

        verify(photoService).upload(eq(horseId), any(UUID.class), any(), any(), any());
    }

    // =========================================================================
    // 7. VETERINARIAN AND STABLE_STAFF READ-ONLY INTEGRITY
    // =========================================================================

    @Test
    void veterinarianCanReadMainProfileFile() throws Exception {
        UUID horseId = UUID.randomUUID();
        when(profileFileService.getActive(horseId)).thenReturn(sampleProfileResponse(horseId));

        mockMvc.perform(get("/api/horses/" + horseId + "/main-file")
                        .with(mockJwt("ROLE_VETERINARIAN", "PERM_PROFILE_VIEW")))
                .andExpect(status().isOk());

        verify(horseService).getByIdForUser(eq(horseId), any(UUID.class), eq(Role.VETERINARIAN));
    }

    @Test
    void veterinarianCannotReplaceMainProfileFile() throws Exception {
        UUID horseId = UUID.randomUUID();
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/horses/" + horseId + "/main-file");
        builder.with(request -> { request.setMethod(HttpMethod.PUT.name()); return request; });

        mockMvc.perform(builder
                        .file(sampleFile())
                        .with(mockJwt("ROLE_VETERINARIAN", "PERM_PROFILE_VIEW")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(profileFileService);
    }

    @Test
    void stableStaffCanReadMainProfileFile() throws Exception {
        UUID horseId = UUID.randomUUID();
        when(profileFileService.getActive(horseId)).thenReturn(sampleProfileResponse(horseId));

        mockMvc.perform(get("/api/horses/" + horseId + "/main-file")
                        .with(mockJwt("ROLE_STABLE_STAFF", "PERM_PROFILE_VIEW")))
                .andExpect(status().isOk());

        verify(horseService).getByIdForUser(eq(horseId), any(UUID.class), eq(Role.STABLE_STAFF));
    }

    @Test
    void stableStaffCannotReplaceMainProfileFile() throws Exception {
        UUID horseId = UUID.randomUUID();
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/horses/" + horseId + "/main-file");
        builder.with(request -> { request.setMethod(HttpMethod.PUT.name()); return request; });

        mockMvc.perform(builder
                        .file(sampleFile())
                        .with(mockJwt("ROLE_STABLE_STAFF", "PERM_PROFILE_VIEW")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(profileFileService);
    }
}
