package com.racehorse.nutrition.service;

import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.nutrition.dto.*;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StableMealIdentityAndStatusTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private HorseAccessService horseAccess;

    private final ObjectMapper json = new ObjectMapper();
    private static final ZoneId CLUB_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private NutritionPlanService createServiceWithTime(LocalDateTime ldt) {
        Instant instant = ldt.atZone(CLUB_ZONE).toInstant();
        Clock fixedClock = Clock.fixed(instant, CLUB_ZONE);
        return new NutritionPlanService(jdbc, horseAccess, json, fixedClock);
    }

    @BeforeEach
    void setUp() {
        reset(jdbc, horseAccess);
    }

    @Test
    void mealIdentityPreservedWhenMealNameOrTimeEdited() {
        NutritionPlanService service = createServiceWithTime(LocalDateTime.of(2026, 11, 13, 8, 0));
        UUID horseId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        // 1. Create initial plan with meal having explicit ID
        String stableMealId = "meal_fixed_id_123";
        MealItem meal1 = new MealItem(
                stableMealId,
                "Bữa sáng",
                LocalTime.of(6, 30),
                BigDecimal.valueOf(2.5),
                "Cám",
                BigDecimal.valueOf(3.0),
                "Cỏ",
                null
        );

        CreateNutritionPlanRequest req1 = new CreateNutritionPlanRequest(
                LocalDate.of(2026, 11, 13),
                "Initial Diet",
                List.of(meal1)
        );

        when(jdbc.queryForObject(contains("coalesce(max(cast(JSON_VALUE"), eq(Integer.class), eq(horseId)))
                .thenReturn(0);

        service.createPlan(horseId, actor, req1);

        // Capture SQL update for plan v1
        verify(jdbc).update(
                contains("insert into module_records"),
                any(), eq(horseId), any(), any(), contains("\"id\":\"meal_fixed_id_123\"")
        );

        // 2. Edit meal name to "Bữa sáng tăng cường" and time to 07:00, keeping same ID
        MealItem editedMeal = new MealItem(
                stableMealId,
                "Bữa sáng tăng cường",
                LocalTime.of(7, 0),
                BigDecimal.valueOf(3.0),
                "Cám",
                BigDecimal.valueOf(3.5),
                "Cỏ",
                "Vitamin"
        );

        CreateNutritionPlanRequest req2 = new CreateNutritionPlanRequest(
                LocalDate.of(2026, 11, 13),
                "Updated Diet",
                List.of(editedMeal)
        );

        when(jdbc.queryForObject(contains("coalesce(max(cast(JSON_VALUE"), eq(Integer.class), eq(horseId)))
                .thenReturn(1);

        service.createPlan(horseId, actor, req2);

        // Verify version 2 retains the exact same meal ID (invoked twice in total: once for v1, once for v2)
        verify(jdbc, times(2)).update(
                contains("insert into module_records"),
                any(), eq(horseId), any(), any(), contains("\"id\":\"meal_fixed_id_123\"")
        );
    }

    @Test
    void newMealWithoutIdGeneratesDistinctIdentity() {
        NutritionPlanService service = createServiceWithTime(LocalDateTime.of(2026, 11, 13, 8, 0));
        UUID horseId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        MealItem newMeal = new MealItem(
                null, // no ID
                "Bữa xế chiều",
                LocalTime.of(15, 0),
                BigDecimal.valueOf(1.0),
                "Cám",
                BigDecimal.valueOf(2.0),
                "Cỏ",
                null
        );

        CreateNutritionPlanRequest req = new CreateNutritionPlanRequest(
                LocalDate.of(2026, 11, 13),
                "Plan with New Meal",
                List.of(newMeal)
        );

        when(jdbc.queryForObject(contains("coalesce(max(cast(JSON_VALUE"), eq(Integer.class), eq(horseId)))
                .thenReturn(0);

        service.createPlan(horseId, actor, req);

        // Verify that a generated id starting with "meal_" was serialized
        verify(jdbc).update(
                contains("insert into module_records"),
                any(), eq(horseId), any(), any(), contains("\"id\":\"meal_")
        );
    }

    @Test
    void legacyPlanWithoutIdFallsBackToLegacyKeyAndIsReadable() {
        NutritionPlanService service = createServiceWithTime(LocalDateTime.of(2026, 11, 13, 8, 0));
        UUID horseId = UUID.randomUUID();

        // Legacy JSON has no "id" property on meal
        String legacyPlanJson = """
                {
                  "version": 1,
                  "effectiveDate": "2026-10-01",
                  "notes": "Legacy Plan",
                  "meals": [
                    {
                      "mealName": "Bữa sáng (06:30)",
                      "feedingTime": "06:30",
                      "concentrateKg": 2.0,
                      "forageKg": 3.0
                    }
                  ]
                }
                """;

        when(jdbc.query(contains("record_type='NUTRITION_PLAN' and horse_id=?"), any(RowMapper.class), eq(horseId), any()))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getObject("horse_id", UUID.class)).thenReturn(horseId);
                    when(rs.getObject("user_id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getString("status")).thenReturn("ACTIVE");
                    when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(legacyPlanJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        NutritionPlanResponse plan = service.getEffectivePlan(horseId, LocalDate.of(2026, 11, 13));

        assertThat(plan.meals()).hasSize(1);
        MealItem meal = plan.meals().get(0);
        // resolveIdentity falls back to generated legacy key
        assertThat(meal.resolveIdentity()).isEqualTo("meal_0630_bua_sang_06_30");
    }

    @Test
    void derivedStatusPendingBeforeScheduledTime() {
        // Current time: 06:00 on 2026-11-13 (meal is at 07:00)
        NutritionPlanService service = createServiceWithTime(LocalDateTime.of(2026, 11, 13, 6, 0));
        UUID horseId = UUID.randomUUID();

        when(jdbc.queryForList(contains("from horses where id=?"), eq(horseId)))
                .thenReturn(List.of(Map.of("code", "H01", "name", "Flash", "stable_location", "A-01")));

        String planJson = """
                {
                  "version": 1,
                  "effectiveDate": "2026-11-01",
                  "meals": [
                    {"id": "meal_01", "mealName": "Morning", "feedingTime": "07:00", "concentrateKg": 2.0, "forageKg": 3.0}
                  ]
                }
                """;

        when(jdbc.query(contains("record_type='NUTRITION_PLAN' and horse_id=?"), any(RowMapper.class), eq(horseId), any()))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getObject("horse_id", UUID.class)).thenReturn(horseId);
                    when(rs.getObject("user_id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getString("status")).thenReturn("ACTIVE");
                    when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(planJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.query(contains("from daily_feeding_logs"), any(RowMapper.class), eq(horseId), eq(LocalDate.of(2026, 11, 13))))
                .thenReturn(Collections.emptyList());

        DailyFeedingChecklistResponse checklist = service.getDailyChecklist(horseId, LocalDate.of(2026, 11, 13));

        assertThat(checklist.meals()).hasSize(1);
        DailyFeedingChecklistMealItem item = checklist.meals().get(0);
        assertThat(item.confirmed()).isFalse();
        assertThat(item.status()).isEqualTo("PENDING");
    }

    @Test
    void derivedStatusOverdueAfterScheduledTime() {
        // Current time: 07:05 on 2026-11-13 (meal was at 07:00, no grace period)
        NutritionPlanService service = createServiceWithTime(LocalDateTime.of(2026, 11, 13, 7, 5));
        UUID horseId = UUID.randomUUID();

        when(jdbc.queryForList(contains("from horses where id=?"), eq(horseId)))
                .thenReturn(List.of(Map.of("code", "H01", "name", "Flash", "stable_location", "A-01")));

        String planJson = """
                {
                  "version": 1,
                  "effectiveDate": "2026-11-01",
                  "meals": [
                    {"id": "meal_01", "mealName": "Morning", "feedingTime": "07:00", "concentrateKg": 2.0, "forageKg": 3.0}
                  ]
                }
                """;

        when(jdbc.query(contains("record_type='NUTRITION_PLAN' and horse_id=?"), any(RowMapper.class), eq(horseId), any()))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getObject("horse_id", UUID.class)).thenReturn(horseId);
                    when(rs.getObject("user_id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getString("status")).thenReturn("ACTIVE");
                    when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(planJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.query(contains("from daily_feeding_logs"), any(RowMapper.class), eq(horseId), eq(LocalDate.of(2026, 11, 13))))
                .thenReturn(Collections.emptyList());

        DailyFeedingChecklistResponse checklist = service.getDailyChecklist(horseId, LocalDate.of(2026, 11, 13));

        assertThat(checklist.meals()).hasSize(1);
        DailyFeedingChecklistMealItem item = checklist.meals().get(0);
        assertThat(item.confirmed()).isFalse();
        assertThat(item.status()).isEqualTo("OVERDUE");
    }

    @Test
    void derivedStatusConfirmedTakesPrecedenceOverScheduledTime() {
        // Current time: 14:00 on 2026-11-13 (way past 07:00)
        NutritionPlanService service = createServiceWithTime(LocalDateTime.of(2026, 11, 13, 14, 0));
        UUID horseId = UUID.randomUUID();

        when(jdbc.queryForList(contains("from horses where id=?"), eq(horseId)))
                .thenReturn(List.of(Map.of("code", "H01", "name", "Flash", "stable_location", "A-01")));

        String planJson = """
                {
                  "version": 1,
                  "effectiveDate": "2026-11-01",
                  "meals": [
                    {"id": "meal_01", "mealName": "Morning", "feedingTime": "07:00", "concentrateKg": 2.0, "forageKg": 3.0}
                  ]
                }
                """;

        when(jdbc.query(contains("record_type='NUTRITION_PLAN' and horse_id=?"), any(RowMapper.class), eq(horseId), any()))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getObject("horse_id", UUID.class)).thenReturn(horseId);
                    when(rs.getObject("user_id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getString("status")).thenReturn("ACTIVE");
                    when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(planJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        Map<String, Object> log = new HashMap<>();
        log.put("meal_key", "meal_01");
        log.put("confirmed_at", OffsetDateTime.now());
        log.put("confirmed_by_name", "Stable Worker");

        when(jdbc.query(contains("from daily_feeding_logs"), any(RowMapper.class), eq(horseId), eq(LocalDate.of(2026, 11, 13))))
                .thenReturn(List.of(log));

        DailyFeedingChecklistResponse checklist = service.getDailyChecklist(horseId, LocalDate.of(2026, 11, 13));

        assertThat(checklist.meals()).hasSize(1);
        DailyFeedingChecklistMealItem item = checklist.meals().get(0);
        assertThat(item.confirmed()).isTrue();
        assertThat(item.status()).isEqualTo("CONFIRMED");
    }

    @Test
    void derivedStatusFutureDateRemainsPendingAndPastDateRemainsOverdue() {
        // Current time: 10:00 on 2026-11-13
        NutritionPlanService service = createServiceWithTime(LocalDateTime.of(2026, 11, 13, 10, 0));
        UUID horseId = UUID.randomUUID();

        when(jdbc.queryForList(contains("from horses where id=?"), eq(horseId)))
                .thenReturn(List.of(Map.of("code", "H01", "name", "Flash", "stable_location", "A-01")));

        String planJson = """
                {
                  "version": 1,
                  "effectiveDate": "2026-11-01",
                  "meals": [
                    {"id": "meal_01", "mealName": "Morning", "feedingTime": "07:00", "concentrateKg": 2.0, "forageKg": 3.0}
                  ]
                }
                """;

        when(jdbc.query(contains("record_type='NUTRITION_PLAN' and horse_id=?"), any(RowMapper.class), eq(horseId), any()))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getObject("horse_id", UUID.class)).thenReturn(horseId);
                    when(rs.getObject("user_id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getString("status")).thenReturn("ACTIVE");
                    when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(planJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        // Test Future Date: 2026-11-14 -> PENDING
        when(jdbc.query(contains("from daily_feeding_logs"), any(RowMapper.class), eq(horseId), eq(LocalDate.of(2026, 11, 14))))
                .thenReturn(Collections.emptyList());

        DailyFeedingChecklistResponse futureChecklist = service.getDailyChecklist(horseId, LocalDate.of(2026, 11, 14));
        assertThat(futureChecklist.meals().get(0).status()).isEqualTo("PENDING");

        // Test Past Date: 2026-11-10 -> OVERDUE
        when(jdbc.query(contains("from daily_feeding_logs"), any(RowMapper.class), eq(horseId), eq(LocalDate.of(2026, 11, 10))))
                .thenReturn(Collections.emptyList());

        DailyFeedingChecklistResponse pastChecklist = service.getDailyChecklist(horseId, LocalDate.of(2026, 11, 10));
        assertThat(pastChecklist.meals().get(0).status()).isEqualTo("OVERDUE");
    }

    @Test
    void stablesQuickFeedReturnsStallsAndMealsWithDerivedStatus() {
        NutritionPlanService service = createServiceWithTime(LocalDateTime.of(2026, 11, 13, 6, 0));
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
                    when(rs.getString("horse_name")).thenReturn("Thunder");
                    return List.of(mapper.mapRow(rs, 1));
                });

        String planJson = """
                {
                  "version": 1,
                  "effectiveDate": "2026-10-01",
                  "notes": "Standard Diet",
                  "meals": [
                    {"id": "m1", "mealName": "Morning", "feedingTime": "07:00", "concentrateKg": 2.5, "forageKg": 3.0}
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
                    when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(planJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        List<StallQuickFeedItem> quickFeed = service.getStablesQuickFeed(LocalDate.of(2026, 11, 13));

        assertThat(quickFeed).hasSize(1);
        StallQuickFeedItem item = quickFeed.get(0);
        assertThat(item.stableName()).isEqualTo("Barn A");
        assertThat(item.stallCode()).isEqualTo("A-01");
        assertThat(item.horseName()).isEqualTo("Thunder");
        assertThat(item.hasPlan()).isTrue();
        assertThat(item.meals()).hasSize(1);
        QuickFeedMealItem meal = item.meals().get(0);
        assertThat(meal.mealId()).isEqualTo("m1");
        assertThat(meal.mealName()).isEqualTo("Morning");
        assertThat(meal.status()).isEqualTo("PENDING");
    }
}
