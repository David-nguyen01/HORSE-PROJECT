package com.racehorse.media.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.media.dto.HorsePhotoResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HorsePhotoService {

    private static final int MAX_PHOTOS = 10;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final StoredFileService storedFileService;
    private final HorseAccessService horseAccessService;
    private final ProfileFileValidator validator;

    public HorsePhotoService(
            JdbcTemplate jdbc,
            ObjectMapper json,
            StoredFileService storedFileService,
            HorseAccessService horseAccessService,
            ProfileFileValidator validator) {
        this.jdbc = jdbc;
        this.json = json;
        this.storedFileService = storedFileService;
        this.horseAccessService = horseAccessService;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public List<HorsePhotoResponse> list(UUID horseId, UUID userId, boolean privileged) {
        horseAccessService.requireReadable(horseId, userId, privileged);
        String sql = """
                SELECT id, horse_id, data_json, created_at
                FROM dbo.module_records
                WHERE module_code = 'PROFILE'
                  AND record_type = 'HORSE_ADDITIONAL_FILE'
                  AND horse_id = ?
                  AND status = 'ACTIVE'
                ORDER BY CAST(JSON_VALUE(data_json, '$.displayOrder') AS INT) ASC, created_at ASC
                """;
        return jdbc.query(sql, this::mapToResponse, horseId);
    }

    @Transactional
    public HorsePhotoResponse upload(
            UUID horseId,
            UUID actorId,
            MultipartFile file,
            Integer requestedOrder,
            String caption) {
        horseAccessService.requireActive(horseId);

        Integer currentCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.module_records WHERE module_code='PROFILE' AND record_type='HORSE_ADDITIONAL_FILE' AND horse_id=? AND status='ACTIVE'",
                Integer.class,
                horseId);
        if (currentCount != null && currentCount >= MAX_PHOTOS) {
            throw new BusinessException("Ngựa đã đạt giới hạn tối đa " + MAX_PHOTOS + " ảnh bổ sung");
        }

        var validated = validator.validate(file);
        if (validated.category() != ProfileFileCategory.IMAGE) {
            throw new BusinessException("Chỉ chấp nhận file ảnh (JPEG, PNG, WEBP) cho bộ sưu tập ảnh bổ sung");
        }

        List<Integer> existingOrders = jdbc.queryForList(
                "SELECT CAST(JSON_VALUE(data_json, '$.displayOrder') AS INT) FROM dbo.module_records WHERE module_code='PROFILE' AND record_type='HORSE_ADDITIONAL_FILE' AND horse_id=? AND status='ACTIVE'",
                Integer.class,
                horseId);

        int finalOrder;
        if (requestedOrder != null) {
            if (requestedOrder < 1 || requestedOrder > MAX_PHOTOS) {
                throw new BusinessException("Thứ tự hiển thị phải nằm trong khoảng từ 1 đến " + MAX_PHOTOS);
            }
            if (existingOrders.contains(requestedOrder)) {
                throw new BusinessException("Thứ tự hiển thị " + requestedOrder + " đã tồn tại");
            }
            finalOrder = requestedOrder;
        } else {
            finalOrder = 1;
            while (existingOrders.contains(finalOrder) && finalOrder <= MAX_PHOTOS) {
                finalOrder++;
            }
            if (finalOrder > MAX_PHOTOS) {
                throw new BusinessException("Không còn thứ tự hiển thị trống");
            }
        }

        StoredFileService.FileView storedFile = storedFileService.upload(
                actorId, "horses/" + horseId + "/gallery", file);

        UUID recordId = UUID.randomUUID();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("storedFileId", storedFile.id().toString());
        data.put("secureUrl", storedFile.secureUrl());
        data.put("publicId", storedFile.publicId());
        data.put("originalFilename", storedFile.originalFilename());
        data.put("contentType", storedFile.contentType());
        data.put("fileSize", storedFile.fileSize());
        data.put("displayOrder", finalOrder);
        data.put("caption", caption != null && !caption.isBlank() ? caption.trim() : null);

        String sql = """
                INSERT INTO dbo.module_records(
                    id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json, created_at, updated_at
                ) VALUES (?, 'PROFILE', 'HORSE_ADDITIONAL_FILE', 'horse_additional_files', ?, ?, 'ACTIVE', CAST(SYSDATETIMEOFFSET() AS DATE), ?, SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET())
                """;
        jdbc.update(sql, recordId, horseId, actorId, toJson(data));

        return new HorsePhotoResponse(
                recordId,
                horseId,
                storedFile.id(),
                storedFile.secureUrl(),
                storedFile.originalFilename(),
                storedFile.contentType(),
                storedFile.fileSize(),
                finalOrder,
                caption != null && !caption.isBlank() ? caption.trim() : null,
                Instant.now());
    }

    @Transactional
    public void delete(UUID horseId, UUID photoId, UUID actorId) {
        horseAccessService.requireActive(horseId);

        List<String> jsonRows = jdbc.queryForList(
                "SELECT data_json FROM dbo.module_records WHERE id=? AND horse_id=? AND status='ACTIVE' AND module_code='PROFILE' AND record_type='HORSE_ADDITIONAL_FILE'",
                String.class,
                photoId,
                horseId);
        if (jsonRows.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy ảnh bổ sung: " + photoId);
        }

        JsonNode node = read(jsonRows.getFirst());
        String storedFileIdStr = node.path("storedFileId").asText(null);

        jdbc.update(
                "UPDATE dbo.module_records SET status='REMOVED', updated_at=SYSDATETIMEOFFSET() WHERE id=?",
                photoId);

        if (storedFileIdStr != null && !storedFileIdStr.isBlank()) {
            try {
                storedFileService.delete(UUID.fromString(storedFileIdStr));
            } catch (RuntimeException ignored) {
                // If cloud asset already deleted or missing, record remains REMOVED
            }
        }
    }

    private HorsePhotoResponse mapToResponse(ResultSet rs, int rowNum) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID horseId = rs.getObject("horse_id", UUID.class);
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        JsonNode node = read(rs.getString("data_json"));

        UUID storedFileId = uuidOrNull(node.path("storedFileId").asText(null));
        String secureUrl = node.path("secureUrl").asText("");
        String originalFilename = node.path("originalFilename").asText("");
        String contentType = node.path("contentType").asText("");
        long fileSize = node.path("fileSize").asLong(0);
        int displayOrder = node.path("displayOrder").asInt(1);
        String caption = node.path("caption").isNull() ? null : node.path("caption").asText(null);

        return new HorsePhotoResponse(
                id,
                horseId,
                storedFileId,
                secureUrl,
                originalFilename,
                contentType,
                fileSize,
                displayOrder,
                caption,
                createdAt != null ? createdAt.toInstant() : Instant.now());
    }

    private JsonNode read(String value) {
        try {
            return json.readTree(value);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

    private UUID uuidOrNull(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
