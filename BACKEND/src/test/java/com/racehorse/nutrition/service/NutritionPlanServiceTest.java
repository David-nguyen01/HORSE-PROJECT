package com.racehorse.nutrition.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.nutrition.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NutritionPlanServiceTest {

    private JdbcTemplate jdbc;
    private HorseAccessService horseAccess;
    private ObjectMapper json;
    private NutritionPlanService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        horseAccess = mock(HorseAccessService.class);
        json = new ObjectMapper();
        service = new NutritionPlanService(jdbc, horseAccess, json);
    }

    @Test
    void createPlanIncrementsVersionAndSupersedesPrevious() {
        UUID horseId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        when(jdbc.queryForObject(contains("max(cast(JSON_VALUE"), eq(Integer.class), eq(horseId)))
                .thenReturn(1);

        MealItem breakfast = new MealItem("Breakfast", LocalTime.of(6, 30), new BigDecimal("2.5"), "Oats",
                new BigDecimal("4.0"), "Alfalfa", "Biotin");
        MealItem dinner = new MealItem("Dinner", LocalTime.of(18, 0), new BigDecimal("3.0"), "Pellets",
                new BigDecimal("5.0"), "Timothy", "Electrolytes");

        CreateNutritionPlanRequest request = new CreateNutritionPlanRequest(
                LocalDate.of(2026, 9, 25), "Post-race diet", List.of(breakfast, dinner));

        NutritionPlanResponse response = service.createPlan(horseId, actor, request);

        assertThat(response.version()).isEqualTo(2);
        assertThat(response.horseId()).isEqualTo(horseId);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.meals()).hasSize(2);

        // Verify previous plan was superseded
        verify(jdbc).update(contains("update module_records set status='SUPERSEDED'"), eq(horseId));
        // Verify insert
        verify(jdbc).update(contains("insert into module_records"), any(), eq(horseId), eq(actor), any(), anyString());
    }

    @Test
    void createPlanValidatesEmptyMeals() {
        UUID horseId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        CreateNutritionPlanRequest invalid = new CreateNutritionPlanRequest(
                LocalDate.of(2026, 9, 25), "Diet", List.of());

        assertThatThrownBy(() -> service.createPlan(horseId, actor, invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Kế hoạch khẩu phần phải có ít nhất một bữa ăn");
    }

    @Test
    void getDailyChecklistSortsMealsChronologically() {
        UUID horseId = UUID.randomUUID();

        // Mock getActivePlan query
        String jsonVal = """
                {
                  "version": 1,
                  "effectiveDate": "2026-09-25",
                  "notes": "Diet",
                  "meals": [
                    {"mealName": "Dinner", "feedingTime": "18:00", "concentrateKg": 2.5, "forageKg": 4.0},
                    {"mealName": "Breakfast", "feedingTime": "06:30", "concentrateKg": 2.0, "forageKg": 3.0},
                    {"mealName": "Lunch", "feedingTime": "12:00", "concentrateKg": 1.5, "forageKg": 2.0}
                  ]
                }
                """;

        when(jdbc.query(contains("where module_code='STABLE_CARE' and record_type='NUTRITION_PLAN' and horse_id=? and status='ACTIVE'"),
                any(RowMapper.class), eq(horseId)))
                .thenAnswer(inv -> {
                    RowMapper<NutritionPlanResponse> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getObject("horse_id", UUID.class)).thenReturn(horseId);
                    when(rs.getObject("user_id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getString("status")).thenReturn("ACTIVE");
                    when(rs.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(jsonVal);
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.queryForList(contains("select code, name, stable_location from horses"), eq(horseId)))
                .thenReturn(List.of(Map.of("code", "H-001", "name", "Thunder", "stable_location", "Barn A / Stall 1")));

        DailyFeedingChecklistResponse checklist = service.getDailyChecklist(horseId, LocalDate.of(2026, 9, 25));

        assertThat(checklist.horseCode()).isEqualTo("H-001");
        assertThat(checklist.horseName()).isEqualTo("Thunder");
        assertThat(checklist.meals()).hasSize(3);
        // Verify chronological order: Breakfast (06:30) -> Lunch (12:00) -> Dinner (18:00)
        assertThat(checklist.meals().get(0).mealName()).isEqualTo("Breakfast");
        assertThat(checklist.meals().get(1).mealName()).isEqualTo("Lunch");
        assertThat(checklist.meals().get(2).mealName()).isEqualTo("Dinner");
    }

    @Test
    void getActivePlanThrowsWhenNotFound() {
        UUID horseId = UUID.randomUUID();
        when(jdbc.query(contains("and status='ACTIVE'"), any(RowMapper.class), eq(horseId)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getActivePlan(horseId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy kế hoạch khẩu phần đang hoạt động");
    }

    @Test
    void getStableWorkloadCalculatesOccupiedAndEmptyStalls() {
        UUID stableId = UUID.randomUUID();
        UUID stallId = UUID.randomUUID();

        when(jdbc.queryForList(contains("from stables where is_active=1")))
                .thenReturn(List.of(Map.of("id", stableId, "name", "Barn A", "location", "North", "is_active", true)));

        when(jdbc.query(contains("record_type='STALL'"), any(RowMapper.class), eq(stableId.toString())))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(stallId);
                    when(rs.getString("data_json")).thenReturn("{\"stallCode\":\"A-01\",\"isActive\":true}");
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.query(contains("record_type='STALL_ASSIGNMENT'"), any(RowMapper.class), eq(stallId.toString())))
                .thenReturn(List.of());

        Map<String, Object> workload = service.getStableWorkload(LocalDate.of(2026, 10, 12));

        assertThat(workload).containsKey("summary");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) workload.get("summary");
        assertThat(summary.get("totalStalls")).isEqualTo(1);
        assertThat(summary.get("emptyStalls")).isEqualTo(1);
        assertThat(summary.get("occupiedStalls")).isEqualTo(0);
    }

    @Test
    void listHorsesActivePlansReturnsOnlyOccupiedStalls() {
        UUID stableId = UUID.randomUUID();
        UUID stallId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();

        when(jdbc.queryForList(contains("from stables where is_active=1")))
                .thenReturn(List.of(Map.of("id", stableId, "name", "Barn B", "location", "East", "is_active", true)));

        when(jdbc.query(contains("record_type='STALL'"), any(RowMapper.class), eq(stableId.toString())))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(stallId);
                    when(rs.getString("data_json")).thenReturn("{\"stallCode\":\"B-02\",\"isActive\":true}");
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.query(contains("record_type='STALL_ASSIGNMENT'"), any(RowMapper.class), eq(stallId.toString())))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getObject("horse_id", UUID.class)).thenReturn(horseId);
                    when(rs.getString("horse_code")).thenReturn("H-002");
                    when(rs.getString("horse_name")).thenReturn("Flash");
                    return List.of(mapper.mapRow(rs, 1));
                });

        // Mock effective plan not found
        when(jdbc.query(contains("and event_date <= ?"), any(RowMapper.class), eq(horseId), any()))
                .thenReturn(List.of());

        List<Map<String, Object>> activeHorses = service.listHorsesActivePlans(LocalDate.of(2026, 10, 12));

        assertThat(activeHorses).hasSize(1);
        assertThat(activeHorses.get(0).get("horseCode")).isEqualTo("H-002");
        assertThat(activeHorses.get(0).get("stableLocation")).isEqualTo("Barn B / B-02");
        assertThat(activeHorses.get(0).get("status")).isEqualTo("UNASSIGNED");
    }

    @Test
    void getStableWorkloadCalculatesOccupiedStallWithActivePlanMeals() {
        UUID stableId = UUID.randomUUID();
        UUID stallId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        when(jdbc.queryForList(contains("from stables where is_active=1")))
                .thenReturn(List.of(Map.of("id", stableId, "name", "Barn A", "location", "North", "is_active", true)));

        when(jdbc.query(contains("record_type='STALL'"), any(RowMapper.class), eq(stableId.toString())))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(stallId);
                    when(rs.getString("data_json")).thenReturn("{\"stallCode\":\"A-01\",\"isActive\":true}");
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.query(contains("record_type='STALL_ASSIGNMENT'"), any(RowMapper.class), eq(stallId.toString())))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getObject("horse_id", UUID.class)).thenReturn(horseId);
                    when(rs.getString("horse_code")).thenReturn("H-001");
                    when(rs.getString("horse_name")).thenReturn("Champion");
                    return List.of(mapper.mapRow(rs, 1));
                });

        String planJson = """
                {
                  "version": 1,
                  "effectiveDate": "2026-10-01",
                  "notes": "Standard Diet",
                  "meals": [
                    {"mealName": "Morning", "feedingTime": "07:00", "concentrateKg": 2.5, "forageKg": 3.0},
                    {"mealName": "Evening", "feedingTime": "17:00", "concentrateKg": 2.0, "forageKg": 4.0}
                  ]
                }
                """;

        when(jdbc.query(contains("record_type='NUTRITION_PLAN' and horse_id=?"), any(RowMapper.class), eq(horseId), any()))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(planId);
                    when(rs.getObject("horse_id", UUID.class)).thenReturn(horseId);
                    when(rs.getObject("user_id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getString("status")).thenReturn("ACTIVE");
                    when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(planJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        Map<String, Object> workload = service.getStableWorkload(LocalDate.of(2026, 10, 12));

        assertThat(workload).containsKey("summary");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) workload.get("summary");
        assertThat(summary.get("totalStalls")).isEqualTo(1);
        assertThat(summary.get("occupiedStalls")).isEqualTo(1);
        assertThat(summary.get("emptyStalls")).isEqualTo(0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stalls = (List<Map<String, Object>>) workload.get("stalls");
        assertThat(stalls).hasSize(1);
        Map<String, Object> stall = stalls.get(0);
        assertThat(stall.get("hasPlan")).isEqualTo(true);
        assertThat(stall.get("totalMeals")).isEqualTo(2);
        assertThat((Double) stall.get("totalConcentrateKg")).isEqualTo(4.5);
        assertThat((Double) stall.get("totalForageKg")).isEqualTo(7.0);
    }

    @Test
    void getTemplateOptionsEvaluatesHealthAndWaitingForVetSuitability() {
        UUID horseId = UUID.randomUUID();

        // Horse is injured and has active lock
        when(jdbc.queryForList(contains("from horses where id=?"), eq(horseId)))
                .thenReturn(List.of(Map.of(
                        "code", "H06",
                        "name", "Chiến Thắng",
                        "health_status", "INJURED",
                        "horse_status", "INJURED",
                        "intake_status", "STABLED",
                        "active_locks", 1
                )));

        String templateTrainingJson = """
                {"planCode":"STD_TRAINING","planName":"Huấn Luyện","description":"...","targetGroup":"...","meals":[]}
                """;
        String templateRecoveryJson = """
                {"planCode":"STD_RECOVERY","planName":"Hồi Phục","description":"...","targetGroup":"...","meals":[]}
                """;
        String templateStandardJson = """
                {"planCode":"STD_STANDARD","planName":"Tiêu Chuẩn","description":"...","targetGroup":"...","meals":[]}
                """;

        when(jdbc.query(contains("record_type='STANDARD_FEEDING_PLAN'"), any(RowMapper.class)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs1 = mock(java.sql.ResultSet.class);
                    when(rs1.getObject("id")).thenReturn(UUID.randomUUID());
                    when(rs1.getString("data_json")).thenReturn(templateTrainingJson);

                    java.sql.ResultSet rs2 = mock(java.sql.ResultSet.class);
                    when(rs2.getObject("id")).thenReturn(UUID.randomUUID());
                    when(rs2.getString("data_json")).thenReturn(templateRecoveryJson);

                    java.sql.ResultSet rs3 = mock(java.sql.ResultSet.class);
                    when(rs3.getObject("id")).thenReturn(UUID.randomUUID());
                    when(rs3.getString("data_json")).thenReturn(templateStandardJson);

                    return List.of(mapper.mapRow(rs1, 1), mapper.mapRow(rs2, 2), mapper.mapRow(rs3, 3));
                });

        List<FeedingTemplateOptionView> options = service.getTemplateOptionsForHorse(horseId);

        assertThat(options).hasSize(3);
        FeedingTemplateOptionView trainingOpt = options.stream().filter(o -> "STD_TRAINING".equals(o.planCode())).findFirst().orElseThrow();
        assertThat(trainingOpt.suitability()).isEqualTo("INELIGIBLE");
        assertThat(trainingOpt.canApply()).isFalse();

        FeedingTemplateOptionView recoveryOpt = options.stream().filter(o -> "STD_RECOVERY".equals(o.planCode())).findFirst().orElseThrow();
        assertThat(recoveryOpt.suitability()).isEqualTo("RECOMMENDED");
        assertThat(recoveryOpt.canApply()).isTrue();
    }

    @Test
    void applyStandardTemplateRejectsTrainingPlanForInjuredOrLockedHorseWithConflict() {
        UUID horseId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        String templateTrainingJson = """
                {"planCode":"STD_TRAINING","planName":"Khẩu phần Huấn luyện Tiêu chuẩn","description":"...","targetGroup":"...","meals":[{"mealName":"Sáng","feedingTime":"06:30","concentrateKg":1.5,"forageKg":3.0}]}
                """;

        when(jdbc.query(contains("record_type='STANDARD_FEEDING_PLAN'"), any(RowMapper.class), eq("STD_TRAINING")))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getString("data_json")).thenReturn(templateTrainingJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.queryForList(contains("from horses where id=?"), eq(horseId)))
                .thenReturn(List.of(Map.of(
                        "code", "H06",
                        "name", "Chiến Thắng",
                        "health_status", "INJURED",
                        "horse_status", "INJURED",
                        "intake_status", "STABLED",
                        "active_locks", 1
                )));

        ApplyFeedingTemplateRequest request = new ApplyFeedingTemplateRequest("STD_TRAINING", LocalDate.now(), "Test apply", false);

        assertThatThrownBy(() -> service.applyStandardTemplate(horseId, actor, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Xung đột y tế")
                .hasMessageContaining("STD_TRAINING");
    }

    @Test
    void applyStandardTemplateSucceedsForNormalHorseWithStandardPlan() {
        UUID horseId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        String templateStandardJson = """
                {"planCode":"STD_STANDARD","planName":"Khẩu phần Tiêu chuẩn Thường nhật","description":"...","targetGroup":"...","meals":[{"mealName":"Sáng","feedingTime":"07:00","concentrateKg":1.0,"forageKg":3.0}]}
                """;

        when(jdbc.query(contains("record_type='STANDARD_FEEDING_PLAN'"), any(RowMapper.class), eq("STD_STANDARD")))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getString("data_json")).thenReturn(templateStandardJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.queryForList(contains("from horses where id=?"), eq(horseId)))
                .thenReturn(List.of(Map.of(
                        "code", "H01",
                        "name", "Thắng",
                        "health_status", "FIT",
                        "horse_status", "ACTIVE",
                        "intake_status", "STABLED",
                        "active_locks", 0
                )));

        when(jdbc.queryForObject(contains("max(cast(JSON_VALUE"), eq(Integer.class), eq(horseId)))
                .thenReturn(0);

        ApplyFeedingTemplateRequest request = new ApplyFeedingTemplateRequest("STD_STANDARD", LocalDate.now(), null, false);

        NutritionPlanResponse response = service.applyStandardTemplate(horseId, actor, request);

        assertThat(response.version()).isEqualTo(1);
        assertThat(response.meals()).hasSize(1);
        assertThat(response.notes()).contains("Áp dụng từ mẫu duyệt sẵn");
        verify(jdbc).update(contains("update module_records set status='SUPERSEDED'"), eq(horseId));
        verify(jdbc).update(contains("insert into module_records"), any(), eq(horseId), eq(actor), any(), anyString());
    }
}
