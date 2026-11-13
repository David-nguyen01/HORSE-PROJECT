package com.racehorse.servicepackage.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.servicepackage.dto.*;
import com.racehorse.servicepackage.entity.ServicePackageEntity;
import com.racehorse.servicepackage.repository.ServicePackageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ServicePackageService {

    public static final String BROADCAST_TITLE = "Gói dịch vụ đã ngừng cung cấp";
    public static final String BROADCAST_MESSAGE =
            "Một hoặc nhiều gói dịch vụ bạn có thể đang sử dụng đã được Club chuyển sang trạng thái ngừng cung cấp. "
                    + "Nếu ngựa của bạn đang sử dụng gói này, vui lòng chú ý thời hạn dịch vụ đã thanh toán và chuẩn bị "
                    + "lựa chọn một gói đang hoạt động khác trong mục Ví và Dịch vụ để tiếp tục sử dụng dịch vụ của Club.";
    public static final String BROADCAST_NOTIFICATION_TYPE = "SERVICE_PACKAGE_DISCONTINUED";

    private final ServicePackageRepository repository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final UserAccountRepository userAccountRepository;

    @Autowired
    public ServicePackageService(ServicePackageRepository repository, JdbcTemplate jdbc, ObjectMapper json,
                                 UserAccountRepository userAccountRepository) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.json = json;
        this.userAccountRepository = userAccountRepository;
    }

    public ServicePackageService(ServicePackageRepository repository, JdbcTemplate jdbc, ObjectMapper json) {
        this(repository, jdbc, json, null);
    }

    @Transactional(readOnly = true)
    public List<ServicePackageResponse> listActive() {
        return repository.findByIsActiveOrderByDisplayOrderAscCreatedAtAsc(true)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServicePackageResponse> listReserve() {
        return repository.findByIsActiveOrderByDisplayOrderAscCreatedAtAsc(false)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServicePackageResponse get(UUID id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói dịch vụ: " + id));
    }

    @Transactional(readOnly = true)
    public ServicePackageResponse getByCode(String code) {
        return repository.findByCode(code)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói dịch vụ với mã: " + code));
    }

    @Transactional
    public ServicePackageResponse create(UUID actorId, CreateServicePackageRequest req) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsByCode(code)) {
            throw new BusinessException("Mã gói dịch vụ đã tồn tại: " + code);
        }

        int displayOrder = req.displayOrder() != null ? req.displayOrder() : nextDisplayOrder();

        ServicePackageEntity entity = new ServicePackageEntity();
        entity.setCode(code);
        entity.setName(req.name().trim());
        entity.setDescription(req.description() != null ? req.description().trim() : null);
        entity.setPriceVnd(req.priceVnd());
        entity.setDurationDays(req.durationDays());
        entity.setActive(req.isActive() == null || req.isActive());
        entity.setDisplayOrder(displayOrder);
        entity.setCreatedByUserId(actorId);
        entity.setUpdatedByUserId(actorId);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());

        ServicePackageEntity saved = repository.save(entity);
        return toResponse(saved);
    }

    @Transactional
    public ServicePackageResponse update(UUID id, UUID actorId, UpdateServicePackageRequest req) {
        ServicePackageEntity entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói dịch vụ: " + id));

        if (req.version() != null && req.version() != entity.getVersion()) {
            throw new BusinessException("Dữ liệu gói dịch vụ đã được cập nhật bởi phiên làm việc khác. Vui lòng tải lại trang.");
        }

        entity.setName(req.name().trim());
        entity.setDescription(req.description() != null ? req.description().trim() : null);
        entity.setPriceVnd(req.priceVnd());
        entity.setDurationDays(req.durationDays());
        if (req.displayOrder() != null) {
            entity.setDisplayOrder(req.displayOrder());
        }
        entity.setUpdatedByUserId(actorId);
        entity.setUpdatedAt(OffsetDateTime.now());

        ServicePackageEntity saved = repository.save(entity);
        return toResponse(saved);
    }

    @Transactional
    public ServicePackageResponse transfer(UUID id, UUID actorId, boolean targetActive) {
        Optional<ServicePackageEntity> opt = repository.findByIdForUpdate(id);
        ServicePackageEntity entity = (opt != null && opt.isPresent())
                ? opt.get()
                : repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói dịch vụ: " + id));

        if (entity.isActive() == targetActive) {
            return toResponse(entity);
        }

        boolean genuineDiscontinuation = entity.isActive() && !targetActive;
        boolean genuineReactivation = !entity.isActive() && targetActive;

        entity.setActive(targetActive);
        entity.setUpdatedByUserId(actorId);
        entity.setUpdatedAt(OffsetDateTime.now());

        ServicePackageEntity saved = repository.saveAndFlush(entity);
        if (saved == null) {
            saved = repository.save(entity);
        }
        if (saved == null) {
            saved = entity;
        }

        if (genuineDiscontinuation) {
            recordDiscontinuationAndBroadcast(saved, actorId);
        }
        if (genuineReactivation) {
            resolvePendingAlertsOnPackageReactivation(saved.getId(), actorId);
        }

        return toResponse(saved);
    }

    private void recordDiscontinuationAndBroadcast(ServicePackageEntity pkg, UUID actorId) {
        UUID eventId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        long versionToUse = pkg.getVersion();
        if (jdbc != null) {
            try {
                Long maxVer = jdbc.queryForObject(
                        "select max(cast(dbo.json_value(data_json, '$.packageVersion') as bigint)) "
                                + "from dbo.module_records "
                                + "where module_code = 'SYSTEM' "
                                + "  and record_type = 'SERVICE_PACKAGE_DISCONTINUATION' "
                                + "  and dbo.json_value(data_json, '$.packageId') = ?",
                        Long.class, pkg.getId().toString());
                if (maxVer != null && versionToUse <= maxVer) {
                    versionToUse = maxVer + 1;
                }
            } catch (Exception ignored) {}
        }
        if (versionToUse <= 0) {
            versionToUse = 1L;
        }

        // 1. Record discontinuation event snapshot in dbo.module_records
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("eventId", eventId.toString());
        eventData.put("packageId", pkg.getId().toString());
        eventData.put("packageCode", pkg.getCode());
        eventData.put("packageName", pkg.getName());
        eventData.put("packageVersion", versionToUse);
        eventData.put("discontinuedAt", now.toString());
        eventData.put("discontinuedBy", actorId.toString());

        try {
            if (jdbc != null) {
                jdbc.update("insert into dbo.module_records(id,module_code,record_type,user_id,status,event_date,data_json) "
                                + "select ?, 'SYSTEM', 'SERVICE_PACKAGE_DISCONTINUATION', ?, 'ACTIVE', ?, ? "
                                + "where not exists (select 1 from dbo.module_records where module_code='SYSTEM' "
                                + "and record_type='SERVICE_PACKAGE_DISCONTINUATION' "
                                + "and dbo.json_value(data_json, '$.packageId')=? "
                                + "and dbo.json_value(data_json, '$.packageVersion')=?)",
                        eventId, actorId, now.toLocalDate(), json != null ? json.writeValueAsString(eventData) : "{}",
                        pkg.getId().toString(), String.valueOf(versionToUse));
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi ghi bản ghi sự kiện ngừng cung cấp gói dịch vụ", e);
        }

        // 2. Identify eligible HORSE_OWNER accounts at publication time (enabled only)
        List<UUID> recipientIds = resolveEligibleOwnerIds();

        // 3. Dispatch individual persistent notifications with deduplication keys
        for (UUID ownerId : recipientIds) {
            String dedupKey = "pkg-broadcast:" + pkg.getId() + ":" + versionToUse + ":" + ownerId;
            UUID notifId = UUID.randomUUID();
            if (jdbc != null) {
                jdbc.update("insert into dbo.notifications(id,recipient_user_id,notification_type,priority,title,message,related_entity_type,related_entity_id,deduplication_key) "
                                + "select ?,?,?,'HIGH',?,?,'SERVICE_PACKAGE',?,? "
                                + "where not exists(select 1 from dbo.notifications where deduplication_key=?)",
                        notifId, ownerId, BROADCAST_NOTIFICATION_TYPE, BROADCAST_TITLE, BROADCAST_MESSAGE, pkg.getId(), dedupKey, dedupKey);
            }
        }

        // 4. Record targeted per-horse subscription expiry alert snapshots for eligible horses (Flow 2)
        recordTargetedHorseExpiryAlerts(pkg, eventId, versionToUse, now, actorId);
    }

    private void recordTargetedHorseExpiryAlerts(ServicePackageEntity pkg, UUID discontinuationEventId,
                                                long versionToUse, OffsetDateTime now, UUID actorId) {
        if (jdbc == null) return;
        LocalDate today = now.toLocalDate();

        String sql = """
                select h.id as horse_id, ho.owner_id, h.paid_until, h.service_plan_started_on, h.service_next_due_date
                from dbo.horses h
                join dbo.horse_ownerships ho on ho.horse_id = h.id
                where h.archived_at is null
                  and h.active_service_plan = ?
                  and h.payment_status = 'PAID'
                  and h.paid_until >= ?
                """;

        List<Map<String, Object>> eligibleHorses = jdbc.queryForList(sql, pkg.getCode(), today);
        for (Map<String, Object> horse : eligibleHorses) {
            UUID horseId = toUuid(horse.get("horse_id"));
            UUID ownerId = toUuid(horse.get("owner_id"));
            LocalDate paidUntil = horse.get("paid_until") instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) horse.get("paid_until");
            LocalDate startedOn = horse.get("service_plan_started_on") instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) horse.get("service_plan_started_on");
            LocalDate nextDue = horse.get("service_next_due_date") instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) horse.get("service_next_due_date");

            UUID periodId = resolveSubscriptionPeriodId(horseId);

            UUID alertId = UUID.randomUUID();
            Map<String, Object> alertData = new LinkedHashMap<>();
            alertData.put("alertType", "DISCONTINUED_HORSE_EXPIRY");
            alertData.put("discontinuationEventId", discontinuationEventId.toString());
            alertData.put("packageId", pkg.getId().toString());
            alertData.put("packageCode", pkg.getCode());
            alertData.put("packageName", pkg.getName());
            alertData.put("packageVersion", versionToUse);
            alertData.put("discontinuedAt", now.toString());
            alertData.put("discontinuedBy", actorId.toString());
            alertData.put("subscriptionPeriodId", periodId != null ? periodId.toString() : null);
            alertData.put("startedOn", startedOn != null ? startedOn.toString() : null);
            alertData.put("paidUntil", paidUntil != null ? paidUntil.toString() : null);
            alertData.put("nextDueDate", nextDue != null ? nextDue.toString() : null);
            alertData.put("ownerIdAtDiscontinuation", ownerId.toString());
            alertData.put("resolvedAt", null);
            alertData.put("resolutionType", null);
            alertData.put("resolvingPackageCode", null);
            alertData.put("resolvingPeriodId", null);

            String alertJson;
            try {
                alertJson = json != null ? json.writeValueAsString(alertData) : "{}";
            } catch (Exception e) {
                alertJson = "{}";
            }

            jdbc.update("""
                    insert into dbo.module_records(id, module_code, record_type, horse_id, user_id, status, event_date, data_json)
                    select ?, 'SYSTEM', 'DISCONTINUED_HORSE_EXPIRY_ALERT', ?, ?, 'PENDING_EXPIRY', ?, ?
                    where not exists (
                        select 1 from dbo.module_records
                        where module_code = 'SYSTEM'
                          and record_type = 'DISCONTINUED_HORSE_EXPIRY_ALERT'
                          and horse_id = ?
                          and lower(dbo.json_value(data_json, '$.discontinuationEventId')) = lower(?)
                    )
                    """,
                    alertId, horseId, ownerId, today, alertJson,
                    horseId, discontinuationEventId.toString());
        }
    }

    private UUID resolveSubscriptionPeriodId(UUID horseId) {
        if (jdbc == null) return null;
        try {
            List<UUID> ids = jdbc.query(
                    "select id from dbo.module_records "
                            + "where module_code = 'SYSTEM' "
                            + "  and record_type in ('SERVICE_PLAN_PURCHASE', 'SERVICE_PLAN_RENEWAL') "
                            + "  and horse_id = ? "
                            + "  and status = 'PAID' "
                            + "order by created_at desc, id desc",
                    (rs, rowNum) -> rs.getObject(1, UUID.class),
                    horseId);
            return (ids != null && !ids.isEmpty()) ? ids.get(0) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void resolvePendingAlertsOnPackageReactivation(UUID packageId, UUID actorId) {
        if (jdbc == null) return;
        OffsetDateTime now = OffsetDateTime.now();
        LocalDate today = now.toLocalDate();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, data_json from dbo.module_records "
                        + "where module_code = 'SYSTEM' "
                        + "  and record_type = 'DISCONTINUED_HORSE_EXPIRY_ALERT' "
                        + "  and status = 'PENDING_EXPIRY' "
                        + "  and lower(dbo.json_value(data_json, '$.packageId')) = lower(?)",
                packageId.toString());

        for (Map<String, Object> row : rows) {
            UUID id = toUuid(row.get("id"));
            String dataJsonStr = (String) row.get("data_json");
            Map<String, Object> data = new LinkedHashMap<>();
            try {
                if (json != null && dataJsonStr != null) {
                    data = json.readValue(dataJsonStr, Map.class);
                }
            } catch (Exception ignored) {}

            LocalDate paidUntil = null;
            if (data.get("paidUntil") != null) {
                try {
                    paidUntil = LocalDate.parse(data.get("paidUntil").toString());
                } catch (Exception ignored) {}
            }
            if (paidUntil != null && paidUntil.isBefore(today)) {
                // Already expired prior to reactivation - remains expired
                continue;
            }

            data.put("resolvedAt", now.toString());
            data.put("resolutionType", "PACKAGE_REACTIVATED");
            data.put("reactivatedBy", actorId.toString());

            String updatedJson;
            try {
                updatedJson = json != null ? json.writeValueAsString(data) : dataJsonStr;
            } catch (Exception e) {
                updatedJson = dataJsonStr;
            }

            jdbc.update("update dbo.module_records set status = 'RESOLVED', data_json = ?, updated_at = CURRENT_TIMESTAMP where id = ?",
                    updatedJson, id);
        }
    }

    private List<UUID> resolveEligibleOwnerIds() {
        if (userAccountRepository != null) {
            try {
                return userAccountRepository.findByRoles(List.of(Role.HORSE_OWNER)).stream()
                        .map(UserAccount::getId)
                        .toList();
            } catch (Exception ignored) {}
        }
        if (jdbc != null) {
            return jdbc.query(
                    "select distinct u.id from dbo.users u join dbo.user_roles ur on ur.user_id=u.id "
                            + "where ur.role='HORSE_OWNER' and cast(u.enabled as varchar(5)) in ('1', 'true', 't')",
                    (rs, rowNum) -> rs.getObject("id", UUID.class));
        }
        return List.of();
    }

    @Transactional
    public List<ServicePackageResponse> reorder(UUID actorId, List<UUID> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            throw new BusinessException("Danh sách ID sắp xếp không được để trống");
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            UUID pkgId = orderedIds.get(i);
            int newOrder = (i + 1) * 10;
            ServicePackageEntity entity = repository.findById(pkgId).orElse(null);
            if (entity != null) {
                entity.setDisplayOrder(newOrder);
                entity.setUpdatedByUserId(actorId);
                entity.setUpdatedAt(OffsetDateTime.now());
                repository.save(entity);
            }
        }
        return listActive();
    }

    @Transactional
    public void delete(UUID id, UUID actorId) {
        ServicePackageEntity entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói dịch vụ: " + id));

        String code = entity.getCode();

        // 1. Check current subscriptions on horses
        Integer horseRefCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.horses WHERE active_service_plan = ? OR next_service_plan = ?",
                Integer.class, code, code);
        if (horseRefCount != null && horseRefCount > 0) {
            throw new BusinessException("Không thể xóa gói dịch vụ đang được sử dụng bởi "
                    + horseRefCount + " ngựa trong hệ thống. Vui lòng chuyển sang Thư viện dự phòng (Reserve).");
        }

        // 2. Check legacy user account references
        Integer userRefCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.users WHERE active_service_plan = ?",
                Integer.class, code);
        if (userRefCount != null && userRefCount > 0) {
            throw new BusinessException("Không thể xóa gói dịch vụ đang được gán cho tài khoản người dùng. Vui lòng chuyển sang Thư viện dự phòng (Reserve).");
        }

        // 3. Check historical records in module_records (SYSTEM: SERVICE_PLAN_*)
        List<String> records = jdbc.query(
                "SELECT data_json FROM dbo.module_records WHERE module_code = 'SYSTEM' "
                        + "AND record_type IN ('SERVICE_PLAN_PURCHASE', 'SERVICE_PLAN_RENEWAL', 'SERVICE_PLAN_TERMS_ACCEPTANCE')",
                (rs, n) -> rs.getString("data_json"));

        for (String recordJson : records) {
            if (recordJson == null || recordJson.isBlank()) continue;
            try {
                JsonNode node = json.readTree(recordJson);
                String plan = node.path("plan").asText("");
                String pkgCode = node.path("packageCode").asText("");
                String pkgId = node.path("packageId").asText("");
                if (code.equalsIgnoreCase(plan) || code.equalsIgnoreCase(pkgCode) || id.toString().equalsIgnoreCase(pkgId)) {
                    throw new BusinessException("Không thể xóa gói dịch vụ đã có dữ liệu lịch sử đăng ký hoặc thanh toán. Vui lòng chuyển sang Thư viện dự phòng (Reserve).");
                }
            } catch (Exception e) {
                if (e instanceof BusinessException) throw (BusinessException) e;
                if (recordJson.contains("\"" + code + "\"")) {
                    throw new BusinessException("Không thể xóa gói dịch vụ đã có dữ liệu lịch sử đăng ký hoặc thanh toán. Vui lòng chuyển sang Thư viện dự phòng (Reserve).");
                }
            }
        }

        repository.delete(entity);
    }

    private int nextDisplayOrder() {
        Integer max = jdbc.queryForObject(
                "SELECT MAX(display_order) FROM dbo.service_packages",
                Integer.class);
        return (max != null) ? max + 10 : 10;
    }

    private static final Map<Integer, Integer> CP1252_MAP = Map.ofEntries(
            Map.entry(0x20AC, 0x80),
            Map.entry(0x201A, 0x82),
            Map.entry(0x0192, 0x83),
            Map.entry(0x201E, 0x84),
            Map.entry(0x2026, 0x85),
            Map.entry(0x2020, 0x86),
            Map.entry(0x2021, 0x87),
            Map.entry(0x02C6, 0x88),
            Map.entry(0x2030, 0x89),
            Map.entry(0x0160, 0x8A),
            Map.entry(0x2039, 0x8B),
            Map.entry(0x0152, 0x8C),
            Map.entry(0x017D, 0x8E),
            Map.entry(0x2018, 0x91),
            Map.entry(0x2019, 0x92),
            Map.entry(0x201C, 0x93),
            Map.entry(0x201D, 0x94),
            Map.entry(0x2022, 0x95),
            Map.entry(0x2013, 0x96),
            Map.entry(0x2014, 0x97),
            Map.entry(0x02DC, 0x98),
            Map.entry(0x2122, 0x99),
            Map.entry(0x0161, 0x9A),
            Map.entry(0x203A, 0x9B),
            Map.entry(0x0153, 0x9C),
            Map.entry(0x017E, 0x9E),
            Map.entry(0x0178, 0x9F)
    );

    public static String sanitizeMojibake(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (!text.contains("Ã") && !text.contains("Ä") && !text.contains("Æ") && !text.contains("á»") && !text.contains("áº")) {
            return text;
        }

        // Restore missing continuation bytes in known legacy seed strings
        String preprocessed = text
                .replace("khá» e", "khá»\u008fe")
                .replace("khÃ¡Â»Â e", "khá»\u008fe")
                .replace("trá» n", "trá»\u008dn")
                .replace("trÃ¡Â»Â n", "trá»\u008dn");

        byte[] bytes = new byte[preprocessed.length()];
        for (int i = 0; i < preprocessed.length(); i++) {
            char c = preprocessed.charAt(i);
            int code = (int) c;
            Integer mapped = CP1252_MAP.get(code);
            if (mapped != null) {
                bytes[i] = mapped.byteValue();
            } else if (code <= 0xFF) {
                bytes[i] = (byte) code;
            } else {
                return text;
            }
        }

        try {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return text;
        }
    }

    public static UUID toUuid(Object obj) {
        if (obj == null) return null;
        if (obj instanceof UUID u) return u;
        if (obj instanceof String s) return UUID.fromString(s);
        return UUID.fromString(obj.toString());
    }

    public ServicePackageResponse toResponse(ServicePackageEntity entity) {
        return new ServicePackageResponse(
                entity.getId(),
                entity.getCode(),
                sanitizeMojibake(entity.getName()),
                sanitizeMojibake(entity.getDescription()),
                entity.getPriceVnd(),
                entity.getDurationDays(),
                entity.isActive(),
                entity.getDisplayOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
