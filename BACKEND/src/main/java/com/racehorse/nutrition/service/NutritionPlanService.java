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

        NutritionPlanResponse plan = getActivePlan(horseId);

        List<Map<String, Object>> horseRows = jdbc.queryForList(
                "select code, name, stable_location from horses where id=?", horseId);
        String code = horseRows.isEmpty() ? null : (String) horseRows.getFirst().get("code");
        String name = horseRows.isEmpty() ? null : (String) horseRows.getFirst().get("name");
        String stableLocation = horseRows.isEmpty() ? null : (String) horseRows.getFirst().get("stable_location");

        // Sort meals chronologically by feeding time for checklist
        List<MealItem> sortedMeals = new ArrayList<>(plan.meals());
        sortedMeals.sort(Comparator.comparing(MealItem::feedingTime));

        return new DailyFeedingChecklistResponse(
                horseId, code, name, stableLocation, targetDate, plan.version(), sortedMeals
        );
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

    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Dữ liệu JSON khẩu phần không hợp lệ", exception); }
    }

    private String toJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Không thể tạo dữ liệu JSON khẩu phần", exception); }
    }
}
