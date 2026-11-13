package com.racehorse.nutrition.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.nutrition.dto.*;
import com.racehorse.nutrition.util.MealKeyGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class NutritionPlanService {

    private final JdbcTemplate jdbc;
    private final HorseAccessService horseAccess;
    private final ObjectMapper json;
    private final Clock clock;
    private static final ZoneId CLUB_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public NutritionPlanService(JdbcTemplate jdbc, HorseAccessService horseAccess, ObjectMapper json) {
        this(jdbc, horseAccess, json, Clock.system(CLUB_ZONE));
    }

    @Autowired
    public NutritionPlanService(JdbcTemplate jdbc, HorseAccessService horseAccess, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.horseAccess = horseAccess;
        this.json = json;
        this.clock = clock != null ? clock.withZone(CLUB_ZONE) : Clock.system(CLUB_ZONE);
    }

    @Transactional
    public NutritionPlanResponse createPlan(UUID horseId, UUID actor, CreateNutritionPlanRequest request) {
        return createPlan(horseId, actor, request, null);
    }

    @Transactional
    public NutritionPlanResponse createPlan(UUID horseId, UUID actor, CreateNutritionPlanRequest request, String sourceTemplateCode) {
        horseAccess.requireActive(horseId);
        validatePlan(request);

        Integer latestVersion = jdbc.queryForObject(
                "select coalesce(max(cast(dbo.json_value(data_json,'$.version') as int)), 0) from module_records " +
                        "where module_code='STABLE_CARE' and record_type='NUTRITION_PLAN' and horse_id=?",
                Integer.class, horseId);
        int newVersion = (latestVersion == null ? 0 : latestVersion) + 1;

        // Supersede previous active plans
        jdbc.update(
                "update module_records set status='SUPERSEDED', updated_at=CURRENT_TIMESTAMP " +
                        "where module_code='STABLE_CARE' and record_type='NUTRITION_PLAN' and horse_id=? and status='ACTIVE'",
                horseId);

        UUID planId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", newVersion);
        data.put("effectiveDate", request.effectiveDate().toString());
        data.put("notes", request.notes() == null ? null : request.notes().trim());
        if (sourceTemplateCode != null && !sourceTemplateCode.isBlank()) {
            data.put("sourceTemplateCode", sourceTemplateCode.trim());
        }

        List<Map<String, Object>> mealList = serializeMeals(request.meals());
        data.put("meals", mealList);

        jdbc.update(
                "insert into module_records(id,module_code,record_type,source_table,horse_id,user_id,status,event_date,data_json,created_at,updated_at) " +
                        "values(?,'STABLE_CARE','NUTRITION_PLAN','api',?,?,'ACTIVE',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                planId, horseId, actor, request.effectiveDate(), toJson(data));

        return new NutritionPlanResponse(
                planId, horseId, newVersion, request.effectiveDate(), "ACTIVE",
                request.notes() == null ? null : request.notes().trim(),
                request.meals(), actor, now,
                sourceTemplateCode != null && !sourceTemplateCode.isBlank() ? sourceTemplateCode.trim() : null
        );
    }

    @Transactional(readOnly = true)
    public NutritionPlanResponse getEffectivePlan(UUID horseId, LocalDate date) {
        horseAccess.requireActive(horseId);
        LocalDate targetDate = date == null ? LocalDate.now() : date;

        // 1. Look for active plan where event_date <= targetDate (effective on or before target date)
        List<NutritionPlanResponse> rows = jdbc.query(
                "select id, horse_id, user_id, status, event_date, data_json, created_at from module_records " +
                        "where module_code='STABLE_CARE' and record_type='NUTRITION_PLAN' and horse_id=? and status='ACTIVE' and event_date <= ? " +
                        "order by event_date desc, cast(dbo.json_value(data_json,'$.version') as int) desc",
                (rs, rowNum) -> mapResponse(rs), horseId, targetDate);

        if (!rows.isEmpty()) {
            return rows.getFirst();
        }

        // 2. Fallback to active plan if any
        return getActivePlan(horseId);
    }

    @Transactional(readOnly = true)
    public NutritionPlanResponse getActivePlan(UUID horseId) {
        horseAccess.requireActive(horseId);
        List<NutritionPlanResponse> rows = jdbc.query(
                "select id, horse_id, user_id, status, event_date, data_json, created_at from module_records " +
                        "where module_code='STABLE_CARE' and record_type='NUTRITION_PLAN' and horse_id=? and status='ACTIVE' " +
                        "order by created_at desc",
                (rs, rowNum) -> mapResponse(rs), horseId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy kế hoạch khẩu phần đang hoạt động cho ngựa: " + horseId);
        }
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public List<NutritionPlanResponse> getHistory(UUID horseId) {
        horseAccess.requireActive(horseId);
        return jdbc.query(
                "select id, horse_id, user_id, status, event_date, data_json, created_at from module_records " +
                        "where module_code='STABLE_CARE' and record_type='NUTRITION_PLAN' and horse_id=? " +
                        "order by cast(dbo.json_value(data_json,'$.version') as int) desc",
                (rs, rowNum) -> mapResponse(rs), horseId);
    }

    @Transactional(readOnly = true)
    public DailyFeedingChecklistResponse getDailyChecklist(UUID horseId, LocalDate date) {
        horseAccess.requireActive(horseId);
        LocalDate targetDate = date == null ? LocalDate.now() : date;

        List<Map<String, Object>> horseRows = jdbc.queryForList(
                "select code, name, stable_location from horses where id=?", horseId);
        String code = horseRows.isEmpty() ? null : (String) horseRows.getFirst().get("code");
        String name = horseRows.isEmpty() ? null : (String) horseRows.getFirst().get("name");
        String stableLocation = horseRows.isEmpty() ? null : (String) horseRows.getFirst().get("stable_location");

        // Verify horse has an operational stall location
        if (stableLocation == null || stableLocation.isBlank()) {
            throw new BusinessException("Ngựa " + (name != null ? name : code)
                    + " chưa được phân bổ vào ô chuồng, không phát sinh danh sách kiểm tra cho ăn.");
        }

        NutritionPlanResponse plan = getEffectivePlan(horseId, targetDate);

        // Sort meals chronologically by feeding time for checklist
        List<MealItem> sortedMeals = new ArrayList<>(plan.meals());
        sortedMeals.sort(Comparator.comparing(MealItem::feedingTime));

        // Attach persisted daily feeding confirmations
        List<Map<String, Object>> confirmedLogs = queryConfirmedLogs(horseId, targetDate);
        Map<String, Map<String, Object>> logsByKey = new HashMap<>();
        for (Map<String, Object> log : confirmedLogs) {
            String key = (String) log.get("meal_key");
            if (key != null) {
                logsByKey.put(key.toLowerCase(Locale.ROOT), log);
            }
        }

        List<DailyFeedingChecklistMealItem> checklistMeals = new ArrayList<>();
        LocalDate today = LocalDate.now(clock);
        LocalTime nowTime = LocalTime.now(clock);

        for (MealItem meal : sortedMeals) {
            String mealKey = meal.resolveIdentity();
            Map<String, Object> log = logsByKey.get(mealKey.toLowerCase(Locale.ROOT));
            if (log == null) {
                String legacyKey = MealKeyGenerator.generate(meal.mealName(), meal.feedingTime());
                log = logsByKey.get(legacyKey.toLowerCase(Locale.ROOT));
            }
            if (log == null) {
                String legacySlug = MealKeyGenerator.slugify(meal.mealName());
                log = logsByKey.get(legacySlug.toLowerCase(Locale.ROOT));
            }

            boolean isConfirmed = log != null;
            OffsetDateTime confirmedAt = isConfirmed ? (OffsetDateTime) log.get("confirmed_at") : null;
            String confirmedByName = isConfirmed ? (String) log.get("confirmed_by_name") : null;

            String status;
            if (isConfirmed) {
                status = "CONFIRMED";
            } else if (targetDate.isAfter(today)) {
                status = "PENDING";
            } else if (targetDate.isBefore(today)) {
                status = "OVERDUE";
            } else {
                status = nowTime.isBefore(meal.feedingTime()) ? "PENDING" : "OVERDUE";
            }

            checklistMeals.add(new DailyFeedingChecklistMealItem(
                    mealKey,
                    meal.mealName(),
                    meal.feedingTime(),
                    meal.concentrateKg(),
                    meal.concentrateType(),
                    meal.forageKg(),
                    meal.forageType(),
                    meal.supplements(),
                    isConfirmed,
                    confirmedAt,
                    confirmedByName,
                    status
            ));
        }

        return new DailyFeedingChecklistResponse(
                horseId, code, name, stableLocation, targetDate, plan.version(), checklistMeals
        );
    }

    private List<Map<String, Object>> queryConfirmedLogs(UUID horseId, LocalDate targetDate) {
        try {
            return jdbc.query(
                    "select l.meal_key, l.confirmed_at, u.username as confirmed_by_name " +
                            "from daily_feeding_logs l " +
                            "left join users u on u.id = l.confirmed_by_user_id " +
                            "where l.horse_id = ? and l.feeding_date = ?",
                    (rs, rn) -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("meal_key", rs.getString("meal_key"));
                        map.put("confirmed_at", rs.getObject("confirmed_at", OffsetDateTime.class));
                        map.put("confirmed_by_name", rs.getString("confirmed_by_name"));
                        return map;
                    },
                    horseId, targetDate
            );
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStandardPlans() {
        return jdbc.query(
                "select id, data_json, created_at from module_records " +
                        "where module_code='STABLE_CARE' and record_type='STANDARD_FEEDING_PLAN' and status='ACTIVE' " +
                        "order by dbo.json_value(data_json,'$.planName') asc",
                (rs, rowNum) -> mapStandardPlan(rs));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStandardPlan(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            throw new BusinessException("Mã khẩu phần mẫu không được để trống");
        }
        String code = planCode.trim();
        List<Map<String, Object>> rows = jdbc.query(
                "select id, data_json, created_at from module_records " +
                        "where module_code='STABLE_CARE' and record_type='STANDARD_FEEDING_PLAN' and status='ACTIVE' " +
                        "and JSON_VALUE(data_json,'$.planCode')=?",
                (rs, rowNum) -> mapStandardPlan(rs), code);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy khẩu phần mẫu: " + code);
        }
        return rows.getFirst();
    }

    @Transactional
    public Map<String, Object> createStandardPlan(UUID actor, CreateStandardFeedingPlanRequest request) {
        if (request == null) {
            throw new BusinessException("Yêu cầu tạo khẩu phần mẫu không hợp lệ");
        }
        validateStandardPlan(request.planCode(), request.planName(), request.meals());

        String code = request.planCode().trim();

        Integer count = jdbc.queryForObject(
                "select count(*) from module_records where module_code='STABLE_CARE' and record_type='STANDARD_FEEDING_PLAN' " +
                        "and JSON_VALUE(data_json,'$.planCode')=?",
                Integer.class, code);
        if (count != null && count > 0) {
            throw new BusinessException("Mã khẩu phần mẫu đã tồn tại: " + code);
        }

        UUID id = UUID.randomUUID();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("planCode", code);
        data.put("planName", request.planName().trim());
        data.put("description", request.description() == null ? "" : request.description().trim());
        data.put("targetGroup", request.targetGroup() == null ? "" : request.targetGroup().trim());
        data.put("meals", serializeMeals(request.meals()));

        jdbc.update(
                "insert into module_records(id,module_code,record_type,source_table,user_id,status,event_date,data_json,created_at,updated_at) " +
                        "values(?,'STABLE_CARE','STANDARD_FEEDING_PLAN','api',?,'ACTIVE',CAST(SYSDATETIMEOFFSET() AS DATE),?,SYSDATETIMEOFFSET(),SYSDATETIMEOFFSET())",
                id, actor, toJson(data));

        return getStandardPlan(code);
    }

    @Transactional
    public Map<String, Object> updateStandardPlan(String planCode, UUID actor, UpdateStandardFeedingPlanRequest request) {
        if (planCode == null || planCode.isBlank()) {
            throw new BusinessException("Mã khẩu phần mẫu không được để trống");
        }
        String code = planCode.trim();

        List<Map<String, Object>> existing = jdbc.query(
                "select id, data_json from module_records where module_code='STABLE_CARE' and record_type='STANDARD_FEEDING_PLAN' " +
                        "and status='ACTIVE' and JSON_VALUE(data_json,'$.planCode')=?",
                (rs, rn) -> Map.of("id", rs.getObject("id"), "data_json", rs.getString("data_json")),
                code);
        if (existing.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy khẩu phần mẫu: " + code);
        }

        UUID recordId = (UUID) existing.getFirst().get("id");

        if (request.planCode() != null && !request.planCode().isBlank() && !request.planCode().trim().equalsIgnoreCase(code)) {
            throw new BusinessException("Không được phép thay đổi mã khẩu phần mẫu sau khi đã tạo");
        }

        validateStandardPlan(code, request.planName(), request.meals());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("planCode", code);
        data.put("planName", request.planName().trim());
        data.put("description", request.description() == null ? "" : request.description().trim());
        data.put("targetGroup", request.targetGroup() == null ? "" : request.targetGroup().trim());
        data.put("meals", serializeMeals(request.meals()));

        int updated = jdbc.update(
                "update module_records set data_json=?, updated_at=SYSDATETIMEOFFSET() where id=?",
                toJson(data), recordId);
        if (updated == 0) {
            throw new ResourceNotFoundException("Không tìm thấy khẩu phần mẫu: " + code);
        }

        return getStandardPlan(code);
    }

    @Transactional
    public void deleteStandardPlan(String planCode, UUID actor) {
        if (planCode == null || planCode.isBlank()) {
            throw new BusinessException("Mã khẩu phần mẫu không được để trống");
        }
        String code = planCode.trim();

        // 1. Verify existence
        List<UUID> templateIds = jdbc.query(
                "select id from module_records where module_code='STABLE_CARE' and record_type='STANDARD_FEEDING_PLAN' " +
                        "and JSON_VALUE(data_json,'$.planCode')=?",
                (rs, rn) -> rs.getObject("id", UUID.class),
                code);
        if (templateIds.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy khẩu phần mẫu: " + code);
        }

        // 2. Identify active prescriptions sourced from this template and cancel them (preserving history)
        jdbc.update(
                "update module_records set status='CANCELLED', updated_at=SYSDATETIMEOFFSET() " +
                        "where module_code='STABLE_CARE' and record_type='NUTRITION_PLAN' and status='ACTIVE' " +
                        "and JSON_VALUE(data_json,'$.sourceTemplateCode')=?",
                code);

        // 3. Physically delete the shared template record (HARD DELETE)
        int deleted = jdbc.update(
                "delete from module_records where module_code='STABLE_CARE' and record_type='STANDARD_FEEDING_PLAN' " +
                        "and JSON_VALUE(data_json,'$.planCode')=?",
                code);

        if (deleted == 0) {
            throw new ResourceNotFoundException("Không tìm thấy khẩu phần mẫu: " + code);
        }
    }

    @Transactional(readOnly = true)
    public List<FeedingTemplateOptionView> getTemplateOptionsForHorse(UUID horseId) {
        horseAccess.requireActive(horseId);

        List<Map<String, Object>> horseRows = jdbc.queryForList(
                "select code, name, health_status, status as horse_status, intake_status, " +
                        "(select count(*) from training_locks tl where tl.horse_id=horses.id and tl.released_at is null) as active_locks " +
                        "from horses where id=?", horseId);
        if (horseRows.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy ngựa: " + horseId);
        }
        Map<String, Object> h = horseRows.getFirst();
        String healthStatus = (String) h.get("health_status");
        String horseStatus = (String) h.get("horse_status");
        String intakeStatus = (String) h.get("intake_status");
        int lockCount = ((Number) h.get("active_locks")).intValue();

        boolean isInjured = "INJURED".equalsIgnoreCase(healthStatus)
                || "INJURED".equalsIgnoreCase(horseStatus)
                || "QUARANTINED".equalsIgnoreCase(healthStatus);
        boolean hasLock = lockCount > 0;
        boolean isWaitingForVet = "WAITING_FOR_VET".equalsIgnoreCase(intakeStatus)
                || "PENDING_WAITING_ROOM".equalsIgnoreCase(intakeStatus);

        List<Map<String, Object>> standardPlans = listStandardPlans();
        List<FeedingTemplateOptionView> options = new ArrayList<>();

        for (Map<String, Object> plan : standardPlans) {
            String planCode = (String) plan.get("planCode");
            String planName = (String) plan.get("planName");
            String description = (String) plan.get("description");
            String targetGroup = (String) plan.get("targetGroup");
            @SuppressWarnings("unchecked")
            List<MealItem> meals = (List<MealItem>) plan.get("meals");

            String suitability;
            String suitabilityReason;
            boolean canApply;
            boolean requiresConfirmation;

            if ("STD_TRAINING".equalsIgnoreCase(planCode)) {
                if (isInjured || hasLock) {
                    suitability = "INELIGIBLE";
                    canApply = false;
                    requiresConfirmation = false;
                    suitabilityReason = "Không đủ điều kiện: Ngựa đang có hạn chế y tế hoặc lệnh khóa huấn luyện. Nghiêm cấm áp dụng khẩu phần năng lượng cao.";
                } else if (isWaitingForVet) {
                    suitability = "INELIGIBLE";
                    canApply = false;
                    requiresConfirmation = false;
                    suitabilityReason = "Chưa đủ điều kiện: Ngựa đang chờ khám thú y, chưa được kết luận đủ thể lực cho chế độ huấn luyện.";
                } else {
                    suitability = "RECOMMENDED";
                    canApply = true;
                    requiresConfirmation = false;
                    suitabilityReason = "Khuyến nghị: Phù hợp cho ngựa thi đấu và luyện tập thể lực thường xuyên.";
                }
            } else if ("STD_RECOVERY".equalsIgnoreCase(planCode)) {
                if (isInjured || hasLock) {
                    suitability = "RECOMMENDED";
                    canApply = true;
                    requiresConfirmation = false;
                    suitabilityReason = "Khuyến nghị hàng đầu: Tối ưu cho ngựa đang nghỉ ngơi, phục hồi thể lực hoặc theo dõi y tế sau chấn thương.";
                } else if (isWaitingForVet) {
                    suitability = "CONFIRMATION_REQUIRED";
                    canApply = true;
                    requiresConfirmation = true;
                    suitabilityReason = "Cần xác nhận: Ngựa đang chờ khám thú y, có thể áp dụng tạm thời nếu cần chế độ giảm tải.";
                } else {
                    suitability = "ELIGIBLE";
                    canApply = true;
                    requiresConfirmation = false;
                    suitabilityReason = "Có thể áp dụng: Dành cho giai đoạn giảm tải tập luyện hoặc hồi sức.";
                }
            } else { // STD_STANDARD
                if (isInjured || hasLock) {
                    suitability = "CONFIRMATION_REQUIRED";
                    canApply = true;
                    requiresConfirmation = false;
                    suitabilityReason = "Cần lưu ý: Ngựa đang có hạn chế y tế; khuyến nghị ưu tiên dùng khẩu phần phục hồi STD_RECOVERY.";
                } else if (isWaitingForVet) {
                    suitability = "CONFIRMATION_REQUIRED";
                    canApply = true;
                    requiresConfirmation = true;
                    suitabilityReason = "Cần xác nhận: Khẩu phần cơ bản an toàn trong thời gian chờ Bác sĩ thú y hoàn tất khám lâm sàng.";
                } else {
                    suitability = "RECOMMENDED";
                    canApply = true;
                    requiresConfirmation = false;
                    suitabilityReason = "Khuyến nghị tiêu chuẩn: Cân bằng dưỡng chất, phù hợp cho ngựa thể trạng bình thường trong chăm sóc hàng ngày.";
                }
            }

            options.add(new FeedingTemplateOptionView(
                    planCode, planName, description, targetGroup,
                    suitability, suitabilityReason, canApply, requiresConfirmation, meals
            ));
        }

        return options;
    }

    @Transactional
    public NutritionPlanResponse applyStandardTemplate(UUID horseId, UUID actor, ApplyFeedingTemplateRequest request) {
        horseAccess.requireActive(horseId);
        if (request.templateCode() == null || request.templateCode().isBlank()) {
            throw new BusinessException("Mã khẩu phần mẫu không được để trống");
        }
        String tCode = request.templateCode().trim();

        List<Map<String, Object>> templateRows = jdbc.query(
                "select data_json from module_records where module_code='STABLE_CARE' and record_type='STANDARD_FEEDING_PLAN' " +
                        "and status='ACTIVE' and dbo.json_value(data_json,'$.planCode')=?",
                (rs, rowNum) -> {
                    JsonNode n = read(rs.getString("data_json"));
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("planCode", n.path("planCode").asText(""));
                    map.put("planName", n.path("planName").asText(""));
                    map.put("description", n.path("description").asText(""));
                    map.put("targetGroup", n.path("targetGroup").asText(""));
                    List<MealItem> meals = new ArrayList<>();
                    JsonNode mealsNode = n.path("meals");
                    if (mealsNode.isArray()) {
                        for (JsonNode m : mealsNode) {
                            String mealId = m.has("id") && !m.path("id").isNull() && !m.path("id").asText().isBlank()
                                    ? m.path("id").asText() : null;
                            meals.add(new MealItem(
                                    mealId,
                                    m.path("mealName").asText(""),
                                    LocalTime.parse(m.path("feedingTime").asText("07:00")),
                                    new BigDecimal(m.path("concentrateKg").asText("0")),
                                    m.path("concentrateType").isNull() ? null : m.path("concentrateType").asText(null),
                                    new BigDecimal(m.path("forageKg").asText("0")),
                                    m.path("forageType").isNull() ? null : m.path("forageType").asText(null),
                                    m.path("supplements").isNull() ? null : m.path("supplements").asText(null)
                            ));
                        }
                    }
                    map.put("meals", meals);
                    return map;
                }, tCode);

        if (templateRows.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy khẩu phần mẫu: " + tCode);
        }
        Map<String, Object> template = templateRows.getFirst();
        String planName = (String) template.get("planName");
        @SuppressWarnings("unchecked")
        List<MealItem> templateMeals = (List<MealItem>) template.get("meals");

        // Clinical guardrail checks
        List<Map<String, Object>> horseRows = jdbc.queryForList(
                "select code, name, health_status, status as horse_status, intake_status, " +
                        "(select count(*) from training_locks tl where tl.horse_id=horses.id and tl.released_at is null) as active_locks " +
                        "from horses where id=?", horseId);
        if (!horseRows.isEmpty()) {
            Map<String, Object> h = horseRows.getFirst();
            String healthStatus = (String) h.get("health_status");
            String horseStatus = (String) h.get("horse_status");
            String intakeStatus = (String) h.get("intake_status");
            int lockCount = ((Number) h.get("active_locks")).intValue();

            boolean isInjured = "INJURED".equalsIgnoreCase(healthStatus)
                    || "INJURED".equalsIgnoreCase(horseStatus)
                    || "QUARANTINED".equalsIgnoreCase(healthStatus);
            boolean hasLock = lockCount > 0;
            boolean isWaitingForVet = "WAITING_FOR_VET".equalsIgnoreCase(intakeStatus)
                    || "PENDING_WAITING_ROOM".equalsIgnoreCase(intakeStatus);

            if ((isInjured || hasLock) && "STD_TRAINING".equalsIgnoreCase(tCode)) {
                throw new BusinessException("Xung đột y tế: Ngựa đang có hạn chế y tế hoặc khóa huấn luyện, hệ thống từ chối áp dụng khẩu phần huấn luyện (STD_TRAINING). Vui lòng chọn khẩu phần dưỡng thương (STD_RECOVERY).");
            }
            if (isWaitingForVet && "STD_TRAINING".equalsIgnoreCase(tCode)) {
                throw new BusinessException("Ngựa đang chờ khám thú y, chưa đủ điều kiện áp dụng khẩu phần huấn luyện (STD_TRAINING).");
            }
        }

        LocalDate effectiveDate = request.effectiveDate() != null ? request.effectiveDate() : LocalDate.now();
        String noteText = (request.notes() != null && !request.notes().isBlank())
                ? request.notes().trim()
                : ("Áp dụng từ mẫu duyệt sẵn: " + planName);

        CreateNutritionPlanRequest createReq = new CreateNutritionPlanRequest(
                effectiveDate,
                noteText,
                templateMeals
        );

        return createPlan(horseId, actor, createReq, tCode);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStableWorkload(LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;

        // 1. Get all active stables
        List<Map<String, Object>> stableRows = jdbc.queryForList(
                "select id, name, location, is_active from stables where is_active=true order by name asc");

        List<Map<String, Object>> stallsList = new ArrayList<>();
        int occupiedCount = 0;
        int emptyCount = 0;

        for (Map<String, Object> stable : stableRows) {
            Object sIdRaw = stable.get("id");
            if (sIdRaw == null) continue;
            UUID stableId = sIdRaw instanceof UUID u ? u : UUID.fromString(sIdRaw.toString());
            String stableName = (String) stable.get("name");

            // Query stalls for this stable
            List<Map<String, Object>> stallRows = jdbc.query(
                    "select id, data_json from module_records where module_code='STABLE_CARE' and record_type='STALL' "
                            + "and dbo.json_value(data_json,'$.stableId')=? order by dbo.json_value(data_json,'$.stallCode') asc",
                    (rs, rowNum) -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("stallId", rs.getObject("id", UUID.class));
                        JsonNode n = read(rs.getString("data_json"));
                        m.put("stallCode", n.path("stallCode").asText(""));
                        m.put("isActive", n.path("isActive").asBoolean(true));
                        return m;
                    }, stableId.toString());

            for (Map<String, Object> stall : stallRows) {
                UUID stallId = (UUID) stall.get("stallId");
                String stallCode = (String) stall.get("stallCode");
                String locationDisplay = stableName + " / " + stallCode;

                // Check active assignment for this stall
                List<Map<String, Object>> assignmentRows = jdbc.query(
                        "select m.id, m.horse_id, h.code as horse_code, h.name as horse_name "
                                + "from module_records m join horses h on h.id=m.horse_id "
                                + "where m.module_code='STABLE_CARE' and m.record_type='STALL_ASSIGNMENT' and m.status='ACTIVE' "
                                + "and dbo.json_value(m.data_json,'$.stallId')=? and dbo.json_value(m.data_json,'$.assignedUntil') is null limit 1",
                        (rs, rowNum) -> {
                            Map<String, Object> a = new LinkedHashMap<>();
                            a.put("assignmentId", rs.getObject("id", UUID.class));
                            a.put("horseId", rs.getObject("horse_id", UUID.class));
                            a.put("horseCode", rs.getString("horse_code"));
                            a.put("horseName", rs.getString("horse_name"));
                            return a;
                        }, stallId.toString());

                Map<String, Object> stallItem = new LinkedHashMap<>();
                stallItem.put("stableId", stableId);
                stallItem.put("stableName", stableName);
                stallItem.put("stallId", stallId);
                stallItem.put("stallCode", stallCode);
                stallItem.put("stallLocationDisplay", locationDisplay);

                if (!assignmentRows.isEmpty()) {
                    Map<String, Object> a = assignmentRows.getFirst();
                    UUID horseId = (UUID) a.get("horseId");
                    String horseCode = (String) a.get("horseCode");
                    String horseName = (String) a.get("horseName");
                    String horseDisplay = horseCode + " — " + horseName;

                    stallItem.put("hasHorse", true);
                    stallItem.put("operationalStatus", "OCCUPIED");
                    stallItem.put("horseId", horseId);
                    stallItem.put("horseCode", horseCode);
                    stallItem.put("horseName", horseName);
                    stallItem.put("horseDisplay", horseDisplay);

                    try {
                        NutritionPlanResponse plan = getEffectivePlan(horseId, targetDate);
                        stallItem.put("hasPlan", true);
                        stallItem.put("planId", plan.id());
                        stallItem.put("planName", plan.notes() != null && !plan.notes().isBlank() ? plan.notes() : "Khẩu phần Huấn luyện Tiêu chuẩn");
                        stallItem.put("version", plan.version());
                        stallItem.put("versionDisplay", "v" + plan.version() + " · Áp dụng");
                        stallItem.put("effectiveDate", plan.effectiveDate());
                        stallItem.put("planStatus", plan.status());
                        stallItem.put("statusLabel", "Áp dụng");
                        stallItem.put("notes", plan.notes());

                        double totalConcentrate = 0.0;
                        double totalForage = 0.0;
                        if (plan.meals() != null) {
                            for (MealItem m : plan.meals()) {
                                if (m.concentrateKg() != null) totalConcentrate += m.concentrateKg().doubleValue();
                                if (m.forageKg() != null) totalForage += m.forageKg().doubleValue();
                            }
                        }
                        int mealCount = plan.meals() == null ? 0 : plan.meals().size();
                        stallItem.put("totalMeals", mealCount);
                        stallItem.put("mealsDisplay", mealCount + " bữa/ngày");
                        stallItem.put("totalConcentrateKg", totalConcentrate);
                        stallItem.put("totalForageKg", totalForage);
                        stallItem.put("feedingSummary", String.format(Locale.US, "%.1fkg cỏ · %.1fkg thức ăn tinh",
                                totalForage, totalConcentrate));
                        stallItem.put("meals", plan.meals());
                        stallItem.put("hasChecklist", true);
                        occupiedCount++;
                    } catch (ResourceNotFoundException ex) {
                        stallItem.put("hasPlan", false);
                        stallItem.put("planId", null);
                        stallItem.put("planName", "Chưa gán khẩu phần");
                        stallItem.put("version", null);
                        stallItem.put("versionDisplay", "—");
                        stallItem.put("effectiveDate", null);
                        stallItem.put("planStatus", "UNASSIGNED");
                        stallItem.put("statusLabel", "Chưa gán khẩu phần");
                        stallItem.put("notes", "Chưa gán khẩu phần");
                        stallItem.put("totalMeals", 0);
                        stallItem.put("mealsDisplay", "0 bữa/ngày");
                        stallItem.put("totalConcentrateKg", 0.0);
                        stallItem.put("totalForageKg", 0.0);
                        stallItem.put("feedingSummary", "Chưa có khẩu phần");
                        stallItem.put("meals", List.of());
                        stallItem.put("hasChecklist", false);
                        occupiedCount++;
                    }
                } else {
                    // Empty stall (Chưa có ngựa)
                    stallItem.put("hasHorse", false);
                    stallItem.put("operationalStatus", "EMPTY");
                    stallItem.put("horseId", null);
                    stallItem.put("horseCode", null);
                    stallItem.put("horseName", "Chưa có ngựa");
                    stallItem.put("horseDisplay", "Chưa có ngựa");
                    stallItem.put("hasPlan", false);
                    stallItem.put("planId", null);
                    stallItem.put("planName", "Không có khẩu phần");
                    stallItem.put("version", null);
                    stallItem.put("versionDisplay", "—");
                    stallItem.put("effectiveDate", null);
                    stallItem.put("planStatus", "EMPTY");
                    stallItem.put("statusLabel", "Chưa có ngựa");
                    stallItem.put("notes", "Chưa có ngựa");
                    stallItem.put("totalMeals", 0);
                    stallItem.put("mealsDisplay", "—");
                    stallItem.put("totalConcentrateKg", 0.0);
                    stallItem.put("totalForageKg", 0.0);
                    stallItem.put("feedingSummary", "Không có khẩu phần");
                    stallItem.put("meals", List.of());
                    stallItem.put("hasChecklist", false);
                    emptyCount++;
                }

                stallsList.add(stallItem);
            }
        }

        // Summary metadata
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalStalls", stallsList.size());
        summary.put("occupiedStalls", occupiedCount);
        summary.put("emptyStalls", emptyCount);
        summary.put("standardPlansCount", listStandardPlans().size());
        summary.put("specialInstructionsCount", 0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetDate", targetDate);
        result.put("summary", summary);
        result.put("stalls", stallsList);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listHorsesActivePlans(LocalDate date) {
        Map<String, Object> workload = getStableWorkload(date);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stalls = (List<Map<String, Object>>) workload.get("stalls");
        List<Map<String, Object>> activeHorses = new ArrayList<>();
        for (Map<String, Object> s : stalls) {
            if (Boolean.TRUE.equals(s.get("hasHorse"))) {
                Map<String, Object> item = new LinkedHashMap<>(s);
                item.put("stableLocation", s.get("stallLocationDisplay"));
                item.put("status", s.get("planStatus"));
                activeHorses.add(item);
            }
        }
        return activeHorses;
    }
    private void validatePlan(CreateNutritionPlanRequest request) {
        if (request.effectiveDate() == null) throw new BusinessException("Ngày áp dụng không được để trống");
        if (request.meals() == null || request.meals().isEmpty()) {
            throw new BusinessException("Kế hoạch khẩu phần phải có ít nhất một bữa ăn");
        }
        for (MealItem item : request.meals()) {
            if (item.mealName() == null || item.mealName().isBlank()) {
                throw new BusinessException("Tên bữa ăn không được để trống");
            }
            if (item.feedingTime() == null) {
                throw new BusinessException("Giờ cho ăn không được để trống");
            }
            if (item.concentrateKg() == null || item.concentrateKg().signum() < 0) {
                throw new BusinessException("Lượng thức ăn tinh không hợp lệ (>= 0)");
            }
            if (item.forageKg() == null || item.forageKg().signum() < 0) {
                throw new BusinessException("Lượng thức ăn thô xanh không hợp lệ (>= 0)");
            }
        }
    }

    private NutritionPlanResponse mapResponse(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID horseId = rs.getObject("horse_id", UUID.class);
        UUID userId = rs.getObject("user_id", UUID.class);
        String status = rs.getString("status");
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        JsonNode node = read(rs.getString("data_json"));

        int version = node.path("version").asInt(1);
        LocalDate effectiveDate = LocalDate.parse(node.path("effectiveDate").asText());
        String notes = node.path("notes").isNull() ? null : node.path("notes").asText(null);
        String sourceTemplateCode = node.path("sourceTemplateCode").isNull() ? null : node.path("sourceTemplateCode").asText(null);

        List<MealItem> meals = new ArrayList<>();
        JsonNode mealsNode = node.path("meals");
        if (mealsNode.isArray()) {
            for (JsonNode m : mealsNode) {
                String mealId = m.has("id") && !m.path("id").isNull() && !m.path("id").asText().isBlank()
                        ? m.path("id").asText() : null;
                meals.add(new MealItem(
                        mealId,
                        m.path("mealName").asText(),
                        LocalTime.parse(m.path("feedingTime").asText()),
                        new BigDecimal(m.path("concentrateKg").asText("0")),
                        m.path("concentrateType").isNull() ? null : m.path("concentrateType").asText(null),
                        new BigDecimal(m.path("forageKg").asText("0")),
                        m.path("forageType").isNull() ? null : m.path("forageType").asText(null),
                        m.path("supplements").isNull() ? null : m.path("supplements").asText(null)
                ));
            }
        }

        return new NutritionPlanResponse(
                id, horseId, version, effectiveDate, status, notes, meals, userId, createdAt, sourceTemplateCode
        );
    }

    @Transactional
    public void deleteActivePlan(UUID horseId, UUID actor) {
        horseAccess.requireActive(horseId);
        int updated = jdbc.update(
                "update module_records set status='CANCELLED', updated_at=CURRENT_TIMESTAMP " +
                        "where module_code='STABLE_CARE' and record_type='NUTRITION_PLAN' and horse_id=? and status='ACTIVE'",
                horseId);
        if (updated == 0) {
            throw new ResourceNotFoundException("Không tìm thấy kế hoạch dinh dưỡng đang kích hoạt của ngựa: " + horseId);
        }
    }

    private Map<String, Object> mapStandardPlan(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rs.getObject("id"));
        JsonNode node = read(rs.getString("data_json"));
        map.put("planCode", node.path("planCode").asText(""));
        map.put("planName", node.path("planName").asText(""));
        map.put("description", node.path("description").asText(""));
        map.put("targetGroup", node.path("targetGroup").asText(""));

        List<MealItem> meals = new ArrayList<>();
        JsonNode mealsNode = node.path("meals");
        if (mealsNode.isArray()) {
            for (JsonNode m : mealsNode) {
                String mealId = m.has("id") && !m.path("id").isNull() && !m.path("id").asText().isBlank()
                        ? m.path("id").asText() : null;
                meals.add(new MealItem(
                        mealId,
                        m.path("mealName").asText(""),
                        LocalTime.parse(m.path("feedingTime").asText("07:00")),
                        new BigDecimal(m.path("concentrateKg").asText("0")),
                        m.path("concentrateType").isNull() ? null : m.path("concentrateType").asText(null),
                        new BigDecimal(m.path("forageKg").asText("0")),
                        m.path("forageType").isNull() ? null : m.path("forageType").asText(null),
                        m.path("supplements").isNull() ? null : m.path("supplements").asText(null)
                ));
            }
        }
        map.put("meals", meals);
        return map;
    }

    private void validateStandardPlan(String planCode, String planName, List<MealItem> meals) {
        if (planCode == null || planCode.isBlank()) {
            throw new BusinessException("Mã khẩu phần mẫu không được để trống");
        }
        if (planName == null || planName.isBlank()) {
            throw new BusinessException("Tên khẩu phần mẫu không được để trống");
        }
        if (meals == null || meals.isEmpty()) {
            throw new BusinessException("Khẩu phần mẫu phải có ít nhất một bữa ăn");
        }
        for (MealItem item : meals) {
            if (item.mealName() == null || item.mealName().isBlank()) {
                throw new BusinessException("Tên bữa ăn không được để trống");
            }
            if (item.feedingTime() == null) {
                throw new BusinessException("Giờ cho ăn không được để trống");
            }
            if (item.concentrateKg() == null || item.concentrateKg().signum() < 0) {
                throw new BusinessException("Lượng thức ăn tinh không hợp lệ (>= 0)");
            }
            if (item.forageKg() == null || item.forageKg().signum() < 0) {
                throw new BusinessException("Lượng thức ăn thô xanh không hợp lệ (>= 0)");
            }
        }
    }

    private List<Map<String, Object>> serializeMeals(List<MealItem> meals) {
        List<Map<String, Object>> mealList = new ArrayList<>();
        if (meals != null) {
            for (MealItem item : meals) {
                Map<String, Object> m = new LinkedHashMap<>();
                String stableId = item.id();
                if (stableId == null || stableId.isBlank()) {
                    stableId = "meal_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                }
                m.put("id", stableId);
                m.put("mealName", item.mealName().trim());
                m.put("feedingTime", item.feedingTime().toString());
                m.put("concentrateKg", item.concentrateKg());
                m.put("concentrateType", item.concentrateType() == null ? null : item.concentrateType().trim());
                m.put("forageKg", item.forageKg());
                m.put("forageType", item.forageType() == null ? null : item.forageType().trim());
                m.put("supplements", item.supplements() == null ? null : item.supplements().trim());
                mealList.add(m);
            }
        }
        return mealList;
    }

    @Transactional(readOnly = true)
    public List<StallQuickFeedItem> getStablesQuickFeed(LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now(clock) : date;
        LocalDate today = LocalDate.now(clock);
        LocalTime nowTime = LocalTime.now(clock);

        Map<String, Object> workload = getStableWorkload(targetDate);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stalls = (List<Map<String, Object>>) workload.get("stalls");
        if (stalls == null || stalls.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> confirmedLogs = queryConfirmedLogsForDate(targetDate);
        Map<UUID, Map<String, Map<String, Object>>> logsByHorse = new HashMap<>();
        for (Map<String, Object> log : confirmedLogs) {
            UUID hId = (UUID) log.get("horse_id");
            String key = (String) log.get("meal_key");
            if (hId != null && key != null) {
                logsByHorse.computeIfAbsent(hId, k -> new HashMap<>()).put(key.toLowerCase(Locale.ROOT), log);
            }
        }

        List<StallQuickFeedItem> result = new ArrayList<>();
        for (Map<String, Object> stall : stalls) {
            boolean hasHorse = Boolean.TRUE.equals(stall.get("hasHorse"));
            if (!hasHorse) {
                continue;
            }

            UUID stableId = (UUID) stall.get("stableId");
            String stableName = (String) stall.get("stableName");
            UUID stallId = (UUID) stall.get("stallId");
            String stallCode = (String) stall.get("stallCode");
            String locationDisplay = (String) stall.get("stallLocationDisplay");

            UUID horseId = (UUID) stall.get("horseId");
            String horseCode = (String) stall.get("horseCode");
            String horseName = (String) stall.get("horseName");
            String horseDisplay = (String) stall.get("horseDisplay");

            boolean hasPlan = Boolean.TRUE.equals(stall.get("hasPlan"));
            UUID planId = (UUID) stall.get("planId");
            int planVersion = stall.get("version") instanceof Number n ? n.intValue() : 0;

            List<QuickFeedMealItem> quickFeedMeals = new ArrayList<>();
            if (hasPlan && stall.get("meals") instanceof List<?> rawMeals) {
                Map<String, Map<String, Object>> horseLogs = logsByHorse.getOrDefault(horseId, Collections.emptyMap());

                for (Object o : rawMeals) {
                    if (o instanceof MealItem meal) {
                        String mealId = meal.resolveIdentity();
                        Map<String, Object> log = horseLogs.get(mealId.toLowerCase(Locale.ROOT));
                        if (log == null) {
                            String legacyKey = MealKeyGenerator.generate(meal.mealName(), meal.feedingTime());
                            log = horseLogs.get(legacyKey.toLowerCase(Locale.ROOT));
                        }
                        if (log == null) {
                            String slugKey = MealKeyGenerator.slugify(meal.mealName());
                            log = horseLogs.get(slugKey.toLowerCase(Locale.ROOT));
                        }

                        boolean isConfirmed = log != null;
                        OffsetDateTime confirmedAt = isConfirmed ? (OffsetDateTime) log.get("confirmed_at") : null;
                        String confirmedByName = isConfirmed ? (String) log.get("confirmed_by_name") : null;

                        String status;
                        if (isConfirmed) {
                            status = "CONFIRMED";
                        } else if (targetDate.isAfter(today)) {
                            status = "PENDING";
                        } else if (targetDate.isBefore(today)) {
                            status = "OVERDUE";
                        } else {
                            status = nowTime.isBefore(meal.feedingTime()) ? "PENDING" : "OVERDUE";
                        }

                        quickFeedMeals.add(new QuickFeedMealItem(
                                mealId,
                                meal.mealName(),
                                meal.feedingTime(),
                                meal.concentrateKg(),
                                meal.concentrateType(),
                                meal.forageKg(),
                                meal.forageType(),
                                meal.supplements(),
                                status,
                                isConfirmed,
                                confirmedAt,
                                confirmedByName
                        ));
                    }
                }
            }

            result.add(new StallQuickFeedItem(
                    stableId,
                    stableName,
                    stallId,
                    stallCode,
                    locationDisplay,
                    horseId,
                    horseCode,
                    horseName,
                    horseDisplay,
                    hasPlan,
                    planId,
                    planVersion,
                    quickFeedMeals
            ));
        }

        return result;
    }

    private List<Map<String, Object>> queryConfirmedLogsForDate(LocalDate date) {
        try {
            return jdbc.query(
                    "select d.horse_id, d.meal_key, d.confirmed_at, d.confirmed_by_user_id, u.full_name as confirmed_by_name " +
                            "from daily_feeding_logs d " +
                            "left join users u on u.id = d.confirmed_by_user_id " +
                            "where d.feeding_date = ?",
                    (rs, rowNum) -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("horse_id", rs.getObject("horse_id", UUID.class));
                        m.put("meal_key", rs.getString("meal_key"));
                        m.put("confirmed_at", rs.getObject("confirmed_at", OffsetDateTime.class));
                        m.put("confirmed_by_user_id", rs.getObject("confirmed_by_user_id", UUID.class));
                        m.put("confirmed_by_name", rs.getString("confirmed_by_name"));
                        return m;
                    }, date);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Dữ liệu JSON khẩu phần không hợp lệ", exception); }
    }

    private String toJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Không thể tạo dữ liệu JSON khẩu phần", exception); }
    }
}
