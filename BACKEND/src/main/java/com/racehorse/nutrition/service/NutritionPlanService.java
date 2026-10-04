package com.racehorse.nutrition.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.nutrition.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class NutritionPlanService {

    private final JdbcTemplate jdbc;
    private final HorseAccessService horseAccess;
    private final ObjectMapper json;

    public NutritionPlanService(JdbcTemplate jdbc, HorseAccessService horseAccess, ObjectMapper json) {
        this.jdbc = jdbc;
        this.horseAccess = horseAccess;
        this.json = json;
    }

    @Transactional
    public NutritionPlanResponse createPlan(UUID horseId, UUID actor, CreateNutritionPlanRequest request) {
        horseAccess.requireActive(horseId);
        validatePlan(request);

        Integer latestVersion = jdbc.queryForObject(
                "select coalesce(max(cast(JSON_VALUE(data_json,'$.version') as int)), 0) from module_records " +
                        "where module_code='STABLE_CARE' and record_type='NUTRITION_PLAN' and horse_id=?",
                Integer.class, horseId);
        int newVersion = (latestVersion == null ? 0 : latestVersion) + 1;

        // Supersede previous active plans
        jdbc.update(
                "update module_records set status='SUPERSEDED', updated_at=SYSDATETIMEOFFSET() " +
                        "where module_code='STABLE_CARE' and record_type='NUTRITION_PLAN' and horse_id=? and status='ACTIVE'",
                horseId);

        UUID planId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", newVersion);
        data.put("effectiveDate", request.effectiveDate().toString());
        data.put("notes", request.notes() == null ? null : request.notes().trim());

        List<Map<String, Object>> mealList = new ArrayList<>();
        for (MealItem item : request.meals()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mealName", item.mealName().trim());
            m.put("feedingTime", item.feedingTime().toString());
            m.put("concentrateKg", item.concentrateKg());
            m.put("concentrateType", item.concentrateType() == null ? null : item.concentrateType().trim());
            m.put("forageKg", item.forageKg());
            m.put("forageType", item.forageType() == null ? null : item.forageType().trim());
            m.put("supplements", item.supplements() == null ? null : item.supplements().trim());
            mealList.add(m);
        }
        data.put("meals", mealList);

        jdbc.update(
                "insert into module_records(id,module_code,record_type,source_table,horse_id,user_id,status,event_date,data_json,created_at,updated_at) " +
                        "values(?,'STABLE_CARE','NUTRITION_PLAN','api',?,?,'ACTIVE',?,?,SYSDATETIMEOFFSET(),SYSDATETIMEOFFSET())",
                planId, horseId, actor, request.effectiveDate(), toJson(data));

        return new NutritionPlanResponse(
                planId, horseId, newVersion, request.effectiveDate(), "ACTIVE",
                request.notes() == null ? null : request.notes().trim(),
                request.meals(), actor, now
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
                        "order by event_date desc, cast(JSON_VALUE(data_json,'$.version') as int) desc",
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
                        "order by cast(JSON_VALUE(data_json,'$.version') as int) desc",
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

        return new DailyFeedingChecklistResponse(
                horseId, code, name, stableLocation, targetDate, plan.version(), sortedMeals
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStandardPlans() {
        return jdbc.query(
                "select id, data_json, created_at from module_records " +
                        "where module_code='STABLE_CARE' and record_type='STANDARD_FEEDING_PLAN' and status='ACTIVE' " +
                        "order by JSON_VALUE(data_json,'$.planName') asc",
                (rs, rowNum) -> {
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
                            meals.add(new MealItem(
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
                });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStableWorkload(LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;

        // 1. Get all active stables
        List<Map<String, Object>> stableRows = jdbc.queryForList(
                "select id, name, location, is_active from stables where is_active=1 order by name asc");

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
                            + "and JSON_VALUE(data_json,'$.stableId')=? order by JSON_VALUE(data_json,'$.stallCode') asc",
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
                        "select top 1 m.id, m.horse_id, h.code as horse_code, h.name as horse_name "
                                + "from module_records m join horses h on h.id=m.horse_id "
                                + "where m.module_code='STABLE_CARE' and m.record_type='STALL_ASSIGNMENT' and m.status='ACTIVE' "
                                + "and JSON_VALUE(m.data_json,'$.stallId')=? and JSON_VALUE(m.data_json,'$.assignedUntil') is null",
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

        List<MealItem> meals = new ArrayList<>();
        JsonNode mealsNode = node.path("meals");
        if (mealsNode.isArray()) {
            for (JsonNode m : mealsNode) {
                meals.add(new MealItem(
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
                id, horseId, version, effectiveDate, status, notes, meals, userId, createdAt
        );
    }

    @Transactional
    public void deleteActivePlan(UUID horseId, UUID actor) {
        horseAccess.requireActive(horseId);
        int updated = jdbc.update(
                "update module_records set status='CANCELLED', updated_at=SYSDATETIMEOFFSET() " +
                        "where module_code='STABLE_CARE' and record_type='NUTRITION_PLAN' and horse_id=? and status='ACTIVE'",
                horseId);
        if (updated == 0) {
            throw new ResourceNotFoundException("Không tìm thấy kế hoạch dinh dưỡng đang kích hoạt của ngựa: " + horseId);
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
