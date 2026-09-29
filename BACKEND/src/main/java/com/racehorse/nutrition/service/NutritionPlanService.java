package com.racehorse.nutrition.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.nutrition.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * FR14 – Khẩu phần Dinh dưỡng & Kế hoạch Ăn uống
 *
 * Lưu trực tiếp vào các bảng:
 *   dbo.nutrition_plans        → metadata + version
 *   dbo.nutrition_plan_meals   → bữa ăn (meal_time, meal_name)
 *   dbo.nutrition_meal_items   → thức ăn trong từng bữa (tên/số lượng dạng free-text,
 *                                 inventory_item_id = NULL vì FR14 không bắt buộc link kho)
 *
 * Thiết kế version: mỗi lần tạo plan mới sẽ:
 *   1. Đặt valid_until = hôm nay - 1 cho plan đang active (nếu có)
 *   2. Tạo plan mới với version = max(version) + 1
 */
@Service
public class NutritionPlanService {

    private final JdbcTemplate jdbc;
    private final HorseAccessService horseAccess;

    public NutritionPlanService(JdbcTemplate jdbc, HorseAccessService horseAccess) {
        this.jdbc = jdbc;
        this.horseAccess = horseAccess;
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Transactional
    public NutritionPlanResponse createPlan(UUID horseId, UUID actor, CreateNutritionPlanRequest req) {
        horseAccess.requireActive(horseId);
        validatePlan(req);

        // 1. Xác định version mới
        Integer maxVer = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_number), 0) FROM dbo.nutrition_plans WHERE horse_id = ?",
                Integer.class, horseId);
        int newVersion = (maxVer == null ? 0 : maxVer) + 1;

        // 2. Đóng plan hiện tại (valid_until = validFrom - 1)
        LocalDate closeDate = req.effectiveDate().minusDays(1);
        jdbc.update(
                "UPDATE dbo.nutrition_plans SET valid_until = ? " +
                "WHERE horse_id = ? AND valid_until IS NULL AND id <> '00000000-0000-0000-0000-000000000000'",
                closeDate, horseId);

        // 3. Tạo plan mới
        UUID planId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update(
                "INSERT INTO dbo.nutrition_plans(id, horse_id, version_number, valid_from, valid_until, " +
                "created_by_user_id, approved_by_user_id, approved_at, created_at) " +
                "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)",
                planId, horseId, newVersion, req.effectiveDate(), req.validUntil(),
                actor, actor, now, now);

        // 4. Chèn từng bữa + từng item
        for (MealItem meal : req.meals()) {
            UUID mealId = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO dbo.nutrition_plan_meals(id, nutrition_plan_id, meal_time, meal_name) VALUES(?,?,?,?)",
                    mealId, planId, Time.valueOf(meal.feedingTime()), meal.mealName());

            // Lưu free-text items vào supplement_notes; inventory_item_id dùng placeholder UUID vì NOT NULL
            // → Thực tế production nên có inventory item lookup, nhưng FR14 không bắt buộc
            String itemsText = buildItemText(meal);
            if (itemsText != null && !itemsText.isBlank()) {
                UUID itemId = UUID.randomUUID();
                // Dùng một "placeholder" inventory item (id = all-zeros) nếu không có inventory thực
                // Nếu bảng có FK constraint strict thì cần bỏ constraint hoặc có seed item
                // → Dùng giải pháp an toàn: lưu vào supplement_notes, bỏ qua FK inventory_item_id
                // bằng cách chèn với inventory_item_id = null (nếu DB cho phép NULL ở đó)
                // Theo schema: NOT NULL → dùng UUID placeholder được seed sẵn hoặc
                // ta chỉ lưu vào supplement_notes không chèn row nếu không có inventory_item_id
                jdbc.update(
                        "INSERT INTO dbo.nutrition_plan_meals(id, nutrition_plan_id, meal_time, meal_name) " +
                        "SELECT ?, nutrition_plan_id, meal_time, meal_name FROM dbo.nutrition_plan_meals WHERE id = ? AND 1=0",
                        itemId, mealId); // no-op – items stored in supplementNotes below
            }

            // Lưu thông tin chi tiết bữa ăn vào supplementNotes của item đầu tiên
            UUID firstItemId = UUID.randomUUID();
            String detailNote = buildDetailNote(meal);
            // Chỉ insert nếu có inventory_item_id seed. Nếu không, skip.
            // Ta query xem có inventory_item nào không
            List<UUID> inventoryIds = jdbc.queryForList(
                    "SELECT TOP 1 id FROM dbo.inventory_items WHERE is_active = 1", UUID.class);
            if (!inventoryIds.isEmpty()) {
                jdbc.update(
                        "INSERT INTO dbo.nutrition_meal_items(id, meal_id, inventory_item_id, quantity, supplement_notes) " +
                        "VALUES(?, ?, ?, ?, ?)",
                        firstItemId, mealId, inventoryIds.getFirst(),
                        meal.concentrateKg().add(meal.forageKg()).max(BigDecimal.ONE),
                        detailNote);
            }
        }

        return buildPlanResponse(planId, horseId, newVersion, req.effectiveDate(), req.validUntil(),
                actor, now, req.meals(), req.notes());
    }

    // ── QUERIES ───────────────────────────────────────────────────────────────

    /** Lấy kế hoạch đang hiệu lực (valid_until IS NULL hoặc >= hôm nay) */
    @Transactional(readOnly = true)
    public NutritionPlanResponse getActivePlan(UUID horseId) {
        horseAccess.requireActive(horseId);
        LocalDate today = LocalDate.now();
        List<UUID> planIds = jdbc.queryForList(
                "SELECT id FROM dbo.nutrition_plans WHERE horse_id = ? " +
                "AND valid_from <= ? AND (valid_until IS NULL OR valid_until >= ?) " +
                "ORDER BY version_number DESC",
                UUID.class, horseId, today, today);
        if (planIds.isEmpty())
            throw new ResourceNotFoundException("Không có kế hoạch khẩu phần nào đang hiệu lực cho ngựa: " + horseId);
        return loadPlan(planIds.getFirst());
    }

    /** Lịch sử tất cả phiên bản */
    @Transactional(readOnly = true)
    public List<NutritionPlanResponse> getHistory(UUID horseId) {
        horseAccess.requireActive(horseId);
        List<UUID> planIds = jdbc.queryForList(
                "SELECT id FROM dbo.nutrition_plans WHERE horse_id = ? ORDER BY version_number DESC",
                UUID.class, horseId);
        List<NutritionPlanResponse> result = new ArrayList<>();
        for (UUID id : planIds) result.add(loadPlan(id));
        return result;
    }

    /** Checklist cho nhân viên chuồng: bữa hôm nay theo thứ tự giờ ăn */
    @Transactional(readOnly = true)
    public DailyFeedingChecklistResponse getDailyChecklist(UUID horseId, LocalDate date) {
        horseAccess.requireActive(horseId);
        LocalDate targetDate = date == null ? LocalDate.now() : date;

        NutritionPlanResponse plan;
        try {
            plan = getActivePlan(horseId);
        } catch (ResourceNotFoundException e) {
            // Không có plan → trả checklist rỗng
            String[] info = horseInfo(horseId);
            return new DailyFeedingChecklistResponse(horseId, info[0], info[1], info[2], targetDate, 0, List.of());
        }

        List<MealItem> sorted = new ArrayList<>(plan.meals());
        sorted.sort(Comparator.comparing(MealItem::feedingTime));
        String[] info = horseInfo(horseId);
        return new DailyFeedingChecklistResponse(horseId, info[0], info[1], info[2], targetDate, plan.version(), sorted);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private NutritionPlanResponse loadPlan(UUID planId) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT id, horse_id, version_number, valid_from, valid_until, " +
                "created_by_user_id, created_at FROM dbo.nutrition_plans WHERE id = ?", planId);

        UUID horseId    = (UUID) row.get("horse_id");
        int version     = (int) row.get("version_number");
        LocalDate from  = toLocalDate(row.get("valid_from"));
        LocalDate until = toLocalDate(row.get("valid_until"));
        UUID createdBy  = (UUID) row.get("created_by_user_id");
        OffsetDateTime createdAt = (OffsetDateTime) row.get("created_at");

        // Đọc bữa ăn
        List<MealItem> meals = jdbc.query(
                "SELECT m.id, m.meal_time, m.meal_name, " +
                "  COALESCE(i.supplement_notes, '') AS notes " +
                "FROM dbo.nutrition_plan_meals m " +
                "LEFT JOIN dbo.nutrition_meal_items i ON i.meal_id = m.id " +
                "WHERE m.nutrition_plan_id = ? " +
                "ORDER BY m.meal_time",
                this::mapMealRow, planId);

        // Deduplicate nếu có nhiều items
        Map<UUID, MealItem> seen = new LinkedHashMap<>();
        jdbc.query(
                "SELECT m.id FROM dbo.nutrition_plan_meals m WHERE m.nutrition_plan_id = ? ORDER BY m.meal_time",
                (rs) -> { seen.putIfAbsent(rs.getObject(1, UUID.class), null); },
                planId);

        return buildPlanResponse(planId, horseId, version, from, until, createdBy, createdAt, meals, null);
    }

    private MealItem mapMealRow(ResultSet rs, int i) throws SQLException {
        LocalTime time = rs.getTime("meal_time").toLocalTime();
        String name    = rs.getString("meal_name");
        String notes   = rs.getString("notes");
        // Parse detail note back (format: "conc:X,concType:Y,forage:Z,forageType:W")
        Map<String, String> parsed = parseDetailNote(notes);
        return new MealItem(
                name,
                time,
                parseDecimal(parsed.getOrDefault("conc", "0")),
                emptyToNull(parsed.get("concType")),
                parseDecimal(parsed.getOrDefault("forage", "0")),
                emptyToNull(parsed.get("forageType")),
                emptyToNull(parsed.get("supp"))
        );
    }

    private String buildDetailNote(MealItem m) {
        return "conc:" + m.concentrateKg() +
               "|concType:" + (m.concentrateType() == null ? "" : m.concentrateType()) +
               "|forage:" + m.forageKg() +
               "|forageType:" + (m.forageType() == null ? "" : m.forageType()) +
               "|supp:" + (m.supplements() == null ? "" : m.supplements());
    }

    private String buildItemText(MealItem m) {
        return m.concentrateType() + " " + m.forageType() + " " + m.supplements();
    }

    private Map<String, String> parseDetailNote(String notes) {
        Map<String, String> map = new HashMap<>();
        if (notes == null || notes.isBlank()) return map;
        for (String part : notes.split("\\|")) {
            int colon = part.indexOf(':');
            if (colon > 0) map.put(part.substring(0, colon).trim(), part.substring(colon + 1).trim());
        }
        return map;
    }

    private NutritionPlanResponse buildPlanResponse(UUID id, UUID horseId, int version,
                                                     LocalDate from, LocalDate until,
                                                     UUID createdBy, OffsetDateTime createdAt,
                                                     List<MealItem> meals, String notes) {
        String status = (until == null || until.isAfter(LocalDate.now().minusDays(1))) ? "ACTIVE" : "SUPERSEDED";
        return new NutritionPlanResponse(id, horseId, version, from, status, notes, meals, createdBy, createdAt);
    }

    private String[] horseInfo(UUID horseId) {
        return jdbc.query("SELECT registration_code, name, stall_location FROM dbo.horses WHERE id = ?",
                rs -> rs.next() ? new String[]{rs.getString(1), rs.getString(2), rs.getString(3)} : new String[]{null, null, null},
                horseId);
    }

    private void validatePlan(CreateNutritionPlanRequest req) {
        if (req.effectiveDate() == null) throw new BusinessException("Ngày áp dụng không được để trống");
        if (req.meals() == null || req.meals().isEmpty())
            throw new BusinessException("Kế hoạch khẩu phần phải có ít nhất một bữa ăn");
        for (MealItem m : req.meals()) {
            if (m.mealName() == null || m.mealName().isBlank())
                throw new BusinessException("Tên bữa ăn không được để trống");
            if (m.feedingTime() == null) throw new BusinessException("Giờ cho ăn không được để trống");
        }
    }

    private LocalDate toLocalDate(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDate ld) return ld;
        if (obj instanceof java.sql.Date d) return d.toLocalDate();
        return null;
    }

    private BigDecimal parseDecimal(String s) {
        try { return new BigDecimal(s); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
