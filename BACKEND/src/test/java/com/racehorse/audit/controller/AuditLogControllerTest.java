package com.racehorse.audit.controller;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock
    private JdbcTemplate jdbc;

    private AuditLogController controller;

    @BeforeEach
    void setUp() {
        controller = new AuditLogController(jdbc);
    }

    @Test
    void classLevelRbacRestrictsToClubManager() {
        assertThat(AuditLogController.class.getAnnotation(RestController.class)).isNotNull();
        RequestMapping mapping = AuditLogController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/api/admin/audit-logs");

        PreAuthorize preAuth = AuditLogController.class.getAnnotation(PreAuthorize.class);
        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasRole('CLUB_MANAGER')");
    }

    @Test
    void listValidatesPaginationBounds() {
        assertThatThrownBy(() -> controller.list(null, null, null, null, null, null, null, null, -1, 50))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Phân trang không hợp lệ");

        assertThatThrownBy(() -> controller.list(null, null, null, null, null, null, null, null, 0, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Phân trang không hợp lệ");

        assertThatThrownBy(() -> controller.list(null, null, null, null, null, null, null, null, 0, 501))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Phân trang không hợp lệ");
    }

    @Test
    void listExecutesQueryWithFilters() {
        UUID actorId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        AuditLogController.AuditView view = new AuditLogController.AuditView(
                1L, actorId, "admin", now, "HORSE", "CREATE", "HORSE", entityId,
                "Tạo ngựa mới", null, "{\"name\":\"Thunder\"}", "127.0.0.1", "Postman");

        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(view));

        List<AuditLogController.AuditView> result = controller.list(
                actorId, "HORSE", "CREATE", "HORSE", entityId, "Thunder", "2026-10-01", "2026-10-12", 0, 50);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).actorUsername()).isEqualTo("admin");
        assertThat(result.get(0).module()).isEqualTo("HORSE");
    }

    @Test
    void countExecutesCountQuery() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(42);

        Map<String, Object> result = controller.count(null, "TRAINING", null, null, "speed", null, null);

        assertThat(result).containsEntry("total", 42);
    }

    @Test
    void exportProducesCsvWithBomAndHeaders() {
        UUID actorId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        AuditLogController.AuditView view = new AuditLogController.AuditView(
                100L, actorId, "manager_user", now, "STABLE_CARE", "UPDATE", "STALL", entityId,
                "Cập nhật ô chuồng \"A-01\"\nvới ghi chú", null, null, "192.168.1.10", "Mozilla/5.0");

        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(view));

        ResponseEntity<byte[]> response = controller.export(null, "STABLE_CARE", null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .isEqualTo("attachment; filename=nhat-ky-kiem-toan.csv");
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("text/csv;charset=UTF-8");

        byte[] bytes = response.getBody();
        assertThat(bytes).isNotNull();
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv).contains("ID,Thời điểm,Người thao tác,Phân hệ,Hành động,Đối tượng,Mã đối tượng,Mô tả,Địa chỉ IP,Thiết bị");
        assertThat(csv).contains("100");
        assertThat(csv).contains("manager_user");
        assertThat(csv).contains("STABLE_CARE");
        assertThat(csv).contains("\"Cập nhật ô chuồng \"\"A-01\"\" với ghi chú\"");
    }

    @Test
    void getReturnsLogWhenFound() {
        OffsetDateTime now = OffsetDateTime.now();
        AuditLogController.AuditView view = new AuditLogController.AuditView(
                5L, UUID.randomUUID(), "vet1", now, "HEALTH", "INSPECT", "HORSE", UUID.randomUUID(),
                "Khám sức khỏe", null, null, "127.0.0.1", "Agent");

        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(5L)))
                .thenReturn(view);

        AuditLogController.AuditView result = controller.get(5L);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.action()).isEqualTo("INSPECT");
    }

    @Test
    void getThrowsNotFoundWhenMissing() {
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(999L)))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy audit log: 999"));

        assertThatThrownBy(() -> controller.get(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy audit log: 999");
    }
}
