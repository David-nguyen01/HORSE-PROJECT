package com.racehorse.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.racehorse.audit.dto.AuditLogResponse;
import com.racehorse.audit.dto.AuditSettingsResponse;
import com.racehorse.audit.dto.UpdateAuditSettingsRequest;
import com.racehorse.audit.entity.AuditLog;
import com.racehorse.audit.repository.AuditLogRepository;
import com.racehorse.common.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditQueryService {

    private static final String SYSTEM_MODULE = "SYSTEM";
    private static final String AUDIT_SETTINGS_TYPE = "AUDIT_SETTINGS";

    private final AuditLogRepository auditLogRepository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditQueryService(AuditLogRepository auditLogRepository, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.auditLogRepository = auditLogRepository;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> searchLogs(
            String moduleCode,
            String actionCode,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            String query,
            Pageable pageable) {
        return auditLogRepository.search(moduleCode, actionCode, startDate, endDate, query, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AuditSettingsResponse getSettings() {
        return jdbc.query("""
                select data_json, updated_at, user_id
                from module_records
                where module_code = ? and record_type = ?
                """, rs -> {
            if (rs.next()) {
                try {
                    Map<String, Object> data = mapper.readValue(rs.getString("data_json"), Map.class);
                    int retentionDays = (Integer) data.getOrDefault("retention_days", 365);
                    return new AuditSettingsResponse(
                            retentionDays,
                            rs.getObject("user_id", UUID.class),
                            rs.getObject("updated_at", OffsetDateTime.class)
                    );
                } catch (Exception e) {
                    throw new BusinessException("Lỗi đọc cấu hình audit: " + e.getMessage());
                }
            }
            return new AuditSettingsResponse(365, null, null); // Default
        }, SYSTEM_MODULE, AUDIT_SETTINGS_TYPE);
    }

    @Transactional
    public AuditSettingsResponse updateSettings(UUID userId, UpdateAuditSettingsRequest request) {
        try {
            String json = mapper.writeValueAsString(Map.of("retention_days", request.retentionDays()));
            
            int updated = jdbc.update("""
                    update module_records
                    set data_json = ?, user_id = ?, updated_at = ?
                    where module_code = ? and record_type = ?
                    """, json, userId, OffsetDateTime.now(), SYSTEM_MODULE, AUDIT_SETTINGS_TYPE);
                    
            if (updated == 0) {
                jdbc.update("""
                        insert into module_records (
                            id, module_code, record_type, status, data_json, user_id, updated_at, created_at
                        ) values (newid(), ?, ?, 'ACTIVE', ?, ?, ?, ?)
                        """, SYSTEM_MODULE, AUDIT_SETTINGS_TYPE, json, userId, OffsetDateTime.now(), OffsetDateTime.now());
            }
            return getSettings();
        } catch (JsonProcessingException e) {
            throw new BusinessException("Lỗi lưu cấu hình audit");
        }
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActor() != null ? log.getActor().getId() : null,
                log.getActor() != null ? log.getActor().getUsername() : "SYSTEM",
                log.getOccurredAt(),
                log.getModuleCode(),
                log.getActionCode(),
                log.getEntityType(),
                log.getEntityId(),
                log.getDescription(),
                log.getOldValuesJson(),
                log.getNewValuesJson(),
                log.getIpAddress(),
                log.getUserAgent()
        );
    }
}
