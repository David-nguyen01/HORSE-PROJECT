package com.racehorse.nutrition.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.nutrition.dto.ConfirmMealFeedingRequest;
import com.racehorse.nutrition.dto.DailyFeedingLogResponse;
import com.racehorse.nutrition.dto.MealItem;
import com.racehorse.nutrition.dto.NutritionPlanResponse;
import com.racehorse.nutrition.entity.DailyFeedingLogEntity;
import com.racehorse.nutrition.repository.DailyFeedingLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DailyFeedingServiceTest {

    private DailyFeedingLogRepository repository;
    private NutritionPlanService nutritionPlanService;
    private HorseAccessService horseAccess;
    private JdbcTemplate jdbc;
    private ObjectMapper json;
    private DailyFeedingService service;

    private final UUID horseId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 11, 13);

    @BeforeEach
    void setUp() {
        repository = mock(DailyFeedingLogRepository.class);
        nutritionPlanService = mock(NutritionPlanService.class);
        horseAccess = mock(HorseAccessService.class);
        jdbc = mock(JdbcTemplate.class);
        json = new ObjectMapper();
        service = new DailyFeedingService(repository, nutritionPlanService, horseAccess, jdbc, json);

        // Default: horse is active and has stall location
        when(jdbc.queryForList(contains("select code, name, stable_location from horses"), eq(horseId)))
                .thenReturn(List.of(Map.of("code", "H-001", "name", "Thunder", "stable_location", "Chuồng A / A-01")));

        // Default: actor username
        when(jdbc.query(contains("select username from users"), any(RowMapper.class), eq(actorId)))
                .thenReturn(List.of("staff_john"));
    }

    private NutritionPlanResponse samplePlan(int version) {
        MealItem breakfast = new MealItem("Bữa sáng (06:30)", LocalTime.of(6, 30),
                new BigDecimal("2.0"), "Yến mạch", new BigDecimal("3.5"), "Cỏ Alfalfa", "Điện giải: 30g");
        MealItem lunch = new MealItem("Bữa trưa (11:30)", LocalTime.of(11, 30),
                new BigDecimal("1.0"), "Cám viên", new BigDecimal("2.5"), "Cỏ Alfalfa", null);
        MealItem dinner = new MealItem("Bữa chiều (17:00)", LocalTime.of(17, 0),
                new BigDecimal("1.5"), "Yến mạch", new BigDecimal("3.5"), "Cỏ Alfalfa", "Vitamin E: 25g");

        return new NutritionPlanResponse(planId, horseId, version, today, "ACTIVE", "Chế độ chuẩn",
                List.of(breakfast, lunch, dinner), actorId, OffsetDateTime.now(), "STD_TRAINING");
    }

    @Test
    void confirmMealSavesPersistentLogWithServerSnapshot() {
        when(nutritionPlanService.getEffectivePlan(horseId, today)).thenReturn(samplePlan(1));
        when(repository.findByHorseIdAndFeedingDateAndMealKey(eq(horseId), eq(today), anyString()))
                .thenReturn(Optional.empty());

        when(repository.saveAndFlush(any(DailyFeedingLogEntity.class))).thenAnswer(inv -> {
            DailyFeedingLogEntity entity = inv.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        ConfirmMealFeedingRequest req = new ConfirmMealFeedingRequest(today, "meal_0630_bua_sang_06_30", null, null);
        DailyFeedingLogResponse res = service.confirmMeal(horseId, actorId, req);

        assertThat(res).isNotNull();
        assertThat(res.horseId()).isEqualTo(horseId);
        assertThat(res.feedingDate()).isEqualTo(today);
        assertThat(res.mealKey()).isEqualTo("meal_0630_bua_sang_06_30");
        assertThat(res.mealName()).isEqualTo("Bữa sáng (06:30)");
        assertThat(res.feedingTime()).isEqualTo(LocalTime.of(6, 30));
        assertThat(res.planId()).isEqualTo(planId);
        assertThat(res.planVersion()).isEqualTo(1);
        assertThat(res.status()).isEqualTo("CONFIRMED");
        assertThat(res.confirmedByUserId()).isEqualTo(actorId);
        assertThat(res.confirmedByName()).isEqualTo("staff_john");
        assertThat(res.mealSnapshot()).containsEntry("concentrateType", "Yến mạch");
        assertThat(res.mealSnapshot()).containsEntry("forageType", "Cỏ Alfalfa");

        ArgumentCaptor<DailyFeedingLogEntity> captor = ArgumentCaptor.forClass(DailyFeedingLogEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        DailyFeedingLogEntity saved = captor.getValue();
        assertThat(saved.getPlanVersion()).isEqualTo(1);
        assertThat(saved.getMealSnapshotJson()).contains("Điện giải: 30g");
    }

    @Test
    void confirmMealIsIdempotentOnDuplicateRequests() {
        when(nutritionPlanService.getEffectivePlan(horseId, today)).thenReturn(samplePlan(1));

        DailyFeedingLogEntity existing = new DailyFeedingLogEntity(
                UUID.randomUUID(), horseId, today, "meal_0630_bua_sang_06_30", "Bữa sáng (06:30)",
                LocalTime.of(6, 30), planId, 1, "CONFIRMED", actorId,
                OffsetDateTime.now(), "{\"concentrateKg\":2.0}", OffsetDateTime.now()
        );
        when(repository.findByHorseIdAndFeedingDateAndMealKey(horseId, today, "meal_0630_bua_sang_06_30"))
                .thenReturn(Optional.of(existing));

        ConfirmMealFeedingRequest req = new ConfirmMealFeedingRequest(today, "meal_0630_bua_sang_06_30", null, null);
        DailyFeedingLogResponse res = service.confirmMeal(horseId, actorId, req);

        assertThat(res.id()).isEqualTo(existing.getId());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void confirmMealHandlesConcurrentRaceConditionGracefully() {
        when(nutritionPlanService.getEffectivePlan(horseId, today)).thenReturn(samplePlan(1));

        DailyFeedingLogEntity persistedByOther = new DailyFeedingLogEntity(
                UUID.randomUUID(), horseId, today, "meal_0630_bua_sang_06_30", "Bữa sáng (06:30)",
                LocalTime.of(6, 30), planId, 1, "CONFIRMED", actorId,
                OffsetDateTime.now(), "{\"concentrateKg\":2.0}", OffsetDateTime.now()
        );

        // First find returns empty, save throws duplicate key exception, subsequent find returns row
        when(repository.findByHorseIdAndFeedingDateAndMealKey(horseId, today, "meal_0630_bua_sang_06_30"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(persistedByOther));

        when(repository.saveAndFlush(any(DailyFeedingLogEntity.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        ConfirmMealFeedingRequest req = new ConfirmMealFeedingRequest(today, "meal_0630_bua_sang_06_30", null, null);
        DailyFeedingLogResponse res = service.confirmMeal(horseId, actorId, req);

        assertThat(res.id()).isEqualTo(persistedByOther.getId());
    }

    @Test
    void confirmMealRejectsHorseWithoutOperationalStall() {
        when(jdbc.queryForList(contains("select code, name, stable_location from horses"), eq(horseId)))
                .thenReturn(List.of(Map.of("code", "H-001", "name", "Thunder", "stable_location", "")));

        ConfirmMealFeedingRequest req = new ConfirmMealFeedingRequest(today, "meal_0630_bua_sang_06_30", null, null);
        assertThatThrownBy(() -> service.confirmMeal(horseId, actorId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa được phân bổ vào ô chuồng");
    }

    @Test
    void confirmMealRejectsHorseWithoutEffectivePlan() {
        when(nutritionPlanService.getEffectivePlan(horseId, today))
                .thenThrow(new ResourceNotFoundException("No plan"));

        ConfirmMealFeedingRequest req = new ConfirmMealFeedingRequest(today, "meal_0630_bua_sang_06_30", null, null);
        assertThatThrownBy(() -> service.confirmMeal(horseId, actorId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa có kế hoạch dinh dưỡng có hiệu lực");
    }

    @Test
    void confirmMealRejectsUnmatchedMeal() {
        when(nutritionPlanService.getEffectivePlan(horseId, today)).thenReturn(samplePlan(1));

        ConfirmMealFeedingRequest req = new ConfirmMealFeedingRequest(today, "meal_9999_non_existent", null, null);
        assertThatThrownBy(() -> service.confirmMeal(horseId, actorId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bữa ăn không tồn tại");
    }

    @Test
    void confirmMealSupportsLegacyPayloadWithoutMealKey() {
        when(nutritionPlanService.getEffectivePlan(horseId, today)).thenReturn(samplePlan(1));
        when(repository.findByHorseIdAndFeedingDateAndMealKey(eq(horseId), eq(today), anyString()))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ConfirmMealFeedingRequest legacyReq = new ConfirmMealFeedingRequest(
                today, null, "Bữa trưa (11:30)", LocalTime.of(11, 30));
        DailyFeedingLogResponse res = service.confirmMeal(horseId, actorId, legacyReq);

        assertThat(res.mealKey()).isEqualTo("meal_1130_bua_trua_11_30");
        assertThat(res.mealName()).isEqualTo("Bữa trưa (11:30)");
        assertThat(res.feedingTime()).isEqualTo(LocalTime.of(11, 30));
    }

    @Test
    void planChangesMidDayPreserveEarlierConfirmationAndSnapshot() {
        // Breakfast confirmed under v1
        NutritionPlanResponse planV1 = samplePlan(1);
        when(nutritionPlanService.getEffectivePlan(horseId, today)).thenReturn(planV1);
        when(repository.findByHorseIdAndFeedingDateAndMealKey(eq(horseId), eq(today), eq("meal_0630_bua_sang_06_30")))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        DailyFeedingLogResponse breakfastRes = service.confirmMeal(
                horseId, actorId, new ConfirmMealFeedingRequest(today, "meal_0630_bua_sang_06_30", null, null));
        assertThat(breakfastRes.planVersion()).isEqualTo(1);

        // Plan updated to v2 mid-day with higher concentrate for Dinner
        MealItem dinnerV2 = new MealItem("Bữa chiều (17:00)", LocalTime.of(17, 0),
                new BigDecimal("3.0"), "Yến mạch tăng cường", new BigDecimal("4.0"), "Cỏ Alfalfa", "Electrolyte Max");
        NutritionPlanResponse planV2 = new NutritionPlanResponse(UUID.randomUUID(), horseId, 2, today, "ACTIVE",
                "Chế độ v2 tăng cường", List.of(samplePlan(1).meals().get(0), dinnerV2), actorId, OffsetDateTime.now(), "STD_TRAINING");

        when(nutritionPlanService.getEffectivePlan(horseId, today)).thenReturn(planV2);
        when(repository.findByHorseIdAndFeedingDateAndMealKey(eq(horseId), eq(today), eq("meal_1700_bua_chieu_17_00")))
                .thenReturn(Optional.empty());

        DailyFeedingLogResponse dinnerRes = service.confirmMeal(
                horseId, actorId, new ConfirmMealFeedingRequest(today, "meal_1700_bua_chieu_17_00", null, null));

        assertThat(dinnerRes.planVersion()).isEqualTo(2);
        assertThat(dinnerRes.mealSnapshot()).containsEntry("concentrateType", "Yến mạch tăng cường");
        // Breakfast v1 confirmation remains untouched
        assertThat(breakfastRes.planVersion()).isEqualTo(1);
    }

    @Test
    void getHistoryReturnsOrderedListAndDateFiltering() {
        DailyFeedingLogEntity log1 = new DailyFeedingLogEntity(
                UUID.randomUUID(), horseId, today, "meal_0630_bua_sang_06_30", "Bữa sáng (06:30)",
                LocalTime.of(6, 30), planId, 1, "CONFIRMED", actorId,
                OffsetDateTime.now(), "{}", OffsetDateTime.now()
        );

        when(repository.findByHorseIdOrderByFeedingDateDescFeedingTimeDesc(horseId))
                .thenReturn(List.of(log1));

        List<DailyFeedingLogResponse> history = service.getHistory(horseId, null, null);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).mealKey()).isEqualTo("meal_0630_bua_sang_06_30");

        LocalDate startDate = today.minusDays(7);
        LocalDate endDate = today;
        when(repository.findByHorseIdAndFeedingDateBetweenOrderByFeedingDateDescFeedingTimeDesc(horseId, startDate, endDate))
                .thenReturn(List.of(log1));

        List<DailyFeedingLogResponse> filteredHistory = service.getHistory(horseId, startDate, endDate);
        assertThat(filteredHistory).hasSize(1);
    }
}
