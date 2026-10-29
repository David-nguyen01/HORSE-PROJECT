package com.racehorse.health.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.health.dto.BarnHealthMapResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class BarnHealthService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public BarnHealthService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public BarnHealthMapResponse getBarnMap(String statusFilter, UUID stableFilter) {
        final String finalStatusFilter;
        if (statusFilter != null && !statusFilter.isBlank()) {
            String normalized = statusFilter.trim().toUpperCase(Locale.ROOT);
            if (!Set.of("FIT", "MONITOR", "INJURED", "QUARANTINED").contains(normalized)) {
                throw new BusinessException("status chỉ nhận FIT, MONITOR, INJURED hoặc QUARANTINED");
            }
            finalStatusFilter = normalized;
        } else {
            finalStatusFilter = null;
        }

        // 1. Fetch active training locks
        Map<UUID, BarnHealthMapResponse.TrainingLockView> activeLocks = new HashMap<>();
        jdbc.query("""
                select id, horse_id, reason, review_date, locked_at
                from training_locks
                where released_at is null
                """, rs -> {
            UUID lockId = rs.getObject("id", UUID.class);
            UUID horseId = rs.getObject("horse_id", UUID.class);
            String reason = rs.getString("reason");
            LocalDate reviewDate = rs.getObject("review_date", LocalDate.class);
            OffsetDateTime lockedAtOdt = rs.getObject("locked_at", OffsetDateTime.class);
            Instant lockedAt = lockedAtOdt != null ? lockedAtOdt.toInstant() : null;
            activeLocks.put(horseId, new BarnHealthMapResponse.TrainingLockView(
                    true, lockId, reason, reviewDate, lockedAt));
        });

        // 2. Fetch all active horses
        Map<UUID, BarnHealthMapResponse.HorseView> horsesById = new HashMap<>();
        jdbc.query("""
                select id, code, name, gender, health_status, stable_location
                from horses
                where archived_at is null
                """, rs -> {
            UUID id = rs.getObject("id", UUID.class);
            String code = rs.getString("code");
            String name = rs.getString("name");
            String gender = rs.getString("gender");
            String healthStatus = rs.getString("health_status");
            if (healthStatus == null || healthStatus.isBlank()) {
                healthStatus = "UNKNOWN";
            }
            String stableLocation = rs.getString("stable_location");
            BarnHealthMapResponse.TrainingLockView lockView = activeLocks.get(id);

            horsesById.put(id, new BarnHealthMapResponse.HorseView(
                    id, code, name, gender, healthStatus, stableLocation, lockView));
        });

        // 3. Fetch active stall assignments: stallId -> horseId
        Map<UUID, UUID> stallToHorse = new HashMap<>();
        jdbc.query("""
                select horse_id, data_json
                from module_records
                where module_code='STABLE_CARE' and record_type='STALL_ASSIGNMENT' and status='ACTIVE'
                """, rs -> {
            UUID horseId = rs.getObject("horse_id", UUID.class);
            JsonNode node = read(rs.getString("data_json"));
            String stallIdStr = node.path("stallId").asText(null);
            if (stallIdStr != null && !stallIdStr.isBlank()) {
                stallToHorse.put(UUID.fromString(stallIdStr), horseId);
            }
        });

        // 4. Fetch stalls by stableId
        Map<UUID, List<BarnHealthMapResponse.StallView>> stallsByStable = new LinkedHashMap<>();
        jdbc.query("""
                select id, status, data_json
                from module_records
                where module_code='STABLE_CARE' and record_type='STALL'
                order by JSON_VALUE(data_json,'$.stallCode')
                """, rs -> {
            UUID stallId = rs.getObject("id", UUID.class);
            boolean active = "ACTIVE".equalsIgnoreCase(rs.getString("status"));
            JsonNode node = read(rs.getString("data_json"));
            String code = node.path("stallCode").asText("");
            String stableIdStr = node.path("stableId").asText(null);
            if (stableIdStr != null && !stableIdStr.isBlank()) {
                UUID stableId = UUID.fromString(stableIdStr);
                UUID horseId = stallToHorse.get(stallId);
                BarnHealthMapResponse.HorseView horse = horseId != null ? horsesById.get(horseId) : null;
                stallsByStable.computeIfAbsent(stableId, k -> new ArrayList<>())
                        .add(new BarnHealthMapResponse.StallView(stallId, code, active, horse));
            }
        });

        // 5. Fetch stables
        String stableSql = "select id, name, location, is_active from stables where is_active=1 "
                + (stableFilter != null ? "and id=? " : "")
                + "order by name";
        List<BarnHealthMapResponse.StableView> stablesList = new ArrayList<>();
        org.springframework.jdbc.core.RowCallbackHandler stableHandler = rs -> {
            UUID stableId = rs.getObject("id", UUID.class);
            String name = rs.getString("name");
            String location = rs.getString("location");
            boolean active = rs.getBoolean("is_active");
            List<BarnHealthMapResponse.StallView> stalls = stallsByStable.getOrDefault(stableId, List.of());

            List<BarnHealthMapResponse.StallView> filteredStalls;
            if (finalStatusFilter != null) {
                filteredStalls = stalls.stream()
                        .filter(s -> s.horse() != null && finalStatusFilter.equalsIgnoreCase(s.horse().healthStatus()))
                        .toList();
            } else {
                filteredStalls = stalls;
            }

            stablesList.add(new BarnHealthMapResponse.StableView(stableId, name, location, active, filteredStalls));
        };

        if (stableFilter != null) {
            jdbc.query(stableSql, stableHandler, stableFilter);
        } else {
            jdbc.query(stableSql, stableHandler);
        }

        // 6. Compute summary
        int totalAssigned = 0;
        int fitCount = 0;
        int monitorCount = 0;
        int injuredCount = 0;
        int quarantinedCount = 0;
        int lockedCount = 0;

        for (UUID horseId : stallToHorse.values()) {
            BarnHealthMapResponse.HorseView h = horsesById.get(horseId);
            if (h != null) {
                totalAssigned++;
                switch (h.healthStatus().toUpperCase(Locale.ROOT)) {
                    case "FIT" -> fitCount++;
                    case "MONITOR" -> monitorCount++;
                    case "INJURED" -> injuredCount++;
                    case "QUARANTINED" -> quarantinedCount++;
                    default -> { /* Unknown or legacy status must not be reported as FIT. */ }
                }
                if (h.trainingLock() != null && h.trainingLock().isLocked()) {
                    lockedCount++;
                }
            }
        }

        BarnHealthMapResponse.Summary summary = new BarnHealthMapResponse.Summary(
                totalAssigned, fitCount, monitorCount, injuredCount, quarantinedCount, lockedCount);

        return new BarnHealthMapResponse(summary, stablesList);
    }

    private JsonNode read(String value) {
        try {
            return json.readTree(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Dữ liệu JSON ô chuồng không hợp lệ", exception);
        }
    }
}
