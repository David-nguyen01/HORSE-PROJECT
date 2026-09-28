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
}
