package com.racehorse.audit.controller;

import com.racehorse.common.exception.GlobalExceptionHandler;
import com.racehorse.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AuditLogControllerHttpTest.TestConfig.class)
@WebAppConfiguration
class AuditLogControllerHttpTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        public JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }

        @Bean
        public AuditLogController auditLogController(JdbcTemplate jdbc) {
            return new AuditLogController(jdbc);
        }

        @Bean
        public GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
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
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        reset(jdbc);
    }

    // --- 1. Authorization Tests ---

    @Test
    void listUnauthenticatedDenied() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(jdbc);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_VETERINARIAN"})
    void listNonClubManagerForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(jdbc);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_HORSE_OWNER"})
    void listHorseOwnerForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(jdbc);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_CLUB_MANAGER"})
    void listClubManagerAllowed() throws Exception {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_JSON));

        verify(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    // --- 2. Pagination & Parameter Filtering ---

    @Test
    @WithMockUser(authorities = {"ROLE_CLUB_MANAGER"})
    void listNegativePageReturnsConflict() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs?page=-1&size=50"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Phân trang không hợp lệ"));
        verifyNoInteractions(jdbc);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_CLUB_MANAGER"})
    void listZeroSizeReturnsConflict() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs?page=0&size=0"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Phân trang không hợp lệ"));
        verifyNoInteractions(jdbc);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_CLUB_MANAGER"})
    void listExorbitantSizeReturnsConflict() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs?page=0&size=501"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Phân trang không hợp lệ"));
        verifyNoInteractions(jdbc);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_CLUB_MANAGER"})
    void listWithValidFiltersBindsParameters() throws Exception {
        UUID actorId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        AuditLogController.AuditView view = new AuditLogController.AuditView(
                1L, actorId, "admin_user", now, "SECURITY", "LOGIN", "USER", actorId,
                "Đăng nhập thành công", null, null, "127.0.0.1", "Chrome");

        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(view));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("actorId", actorId.toString())
                        .param("module", "SECURITY")
                        .param("action", "LOGIN")
                        .param("query", "admin")
                        .param("page", "1")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].actorUsername").value("admin_user"))
                .andExpect(jsonPath("$[0].module").value("SECURITY"));

        verify(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    // --- 3. Count & Detail Endpoints ---

    @Test
    @WithMockUser(authorities = {"ROLE_CLUB_MANAGER"})
    void countReturnsTotalCount() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(42);

        mockMvc.perform(get("/api/admin/audit-logs/count?module=TRAINING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(42));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_CLUB_MANAGER"})
    void getDetailReturnsLogRecord() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        AuditLogController.AuditView view = new AuditLogController.AuditView(
                15L, UUID.randomUUID(), "trainer1", now, "TRAINING", "EVALUATE", "HORSE", UUID.randomUUID(),
                "Đánh giá bài tập", null, null, "10.0.0.1", "Mobile");

        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(15L)))
                .thenReturn(view);

        mockMvc.perform(get("/api/admin/audit-logs/15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.module").value("TRAINING"))
                .andExpect(jsonPath("$.actorUsername").value("trainer1"));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_CLUB_MANAGER"})
    void getDetailMissingIdReturnsNotFound() throws Exception {
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(999L)))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy audit log: 999"));

        mockMvc.perform(get("/api/admin/audit-logs/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy audit log: 999"));
    }

    // --- 4. CSV Export Tests ---

    @Test
    @WithMockUser(authorities = {"ROLE_CLUB_MANAGER"})
    void exportProducesValidCsvWithBomAndHeadersAndEscaping() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        AuditLogController.AuditView view = new AuditLogController.AuditView(
                100L, actorId, "admin,lead", now, "HORSE", "UPDATE", "HORSE", horseId,
                "Cập nhật tên ngựa \"Thunder, Fast\"\nvới ghi chú mới",
                null, null, "192.168.1.1", "Agent/1.0");

        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(view));

        MvcResult result = mockMvc.perform(get("/api/admin/audit-logs/export?module=HORSE"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=nhat-ky-kiem-toan.csv"))
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andReturn();

        byte[] contentBytes = result.getResponse().getContentAsByteArray();
        assertThat(contentBytes).isNotEmpty();

        String csv = new String(contentBytes, StandardCharsets.UTF_8);
        // Verify UTF-8 BOM
        assertThat(csv).startsWith("\uFEFF");
        // Verify CSV header columns
        assertThat(csv).contains("ID,Thời điểm,Người thao tác,Phân hệ,Hành động,Đối tượng,Mã đối tượng,Mô tả,Địa chỉ IP,Thiết bị");
        // Verify escaped username with comma
        assertThat(csv).contains("\"admin,lead\"");
        // Verify escaped quotes and stripped newlines
        assertThat(csv).contains("\"Cập nhật tên ngựa \"\"Thunder, Fast\"\" với ghi chú mới\"");
        // Ensure no passwords or sensitive tokens are present
        assertThat(csv).doesNotContain("password").doesNotContain("token").doesNotContain("secret");
    }

    @Test
    @WithMockUser(authorities = {"ROLE_CLUB_MANAGER"})
    void exportEmptyResultProducesHeaderOnly() throws Exception {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        MvcResult result = mockMvc.perform(get("/api/admin/audit-logs/export"))
                .andExpect(status().isOk())
                .andReturn();

        String csv = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv).contains("ID,Thời điểm,Người thao tác,Phân hệ,Hành động,Đối tượng,Mã đối tượng,Mô tả,Địa chỉ IP,Thiết bị\n");
    }
}
