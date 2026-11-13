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
import com.racehorse.nutrition.util.MealKeyGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class DailyFeedingService {

    private final DailyFeedingLogRepository repository;
    private final NutritionPlanService nutritionPlanService;
    private final HorseAccessService horseAccess;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public DailyFeedingService(DailyFeedingLogRepository repository,
                               NutritionPlanService nutritionPlanService,
                               HorseAccessService horseAccess,
                               JdbcTemplate jdbc,
                               ObjectMapper json) {
        this.repository = repository;
        this.nutritionPlanService = nutritionPlanService;
        this.horseAccess = horseAccess;
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public DailyFeedingLogResponse confirmMeal(UUID horseId, UUID actorUserId, ConfirmMealFeedingRequest request) {
        if (request == null) {
            throw new BusinessException("Yêu cầu xác nhận cho ăn không được để trống");
        }
        if (request.date() == null) {
            throw new BusinessException("Ngày cho ăn không được để trống");
        }

        // 1. Verify horse is active
        horseAccess.requireActive(horseId);

        // 2. Verify horse is assigned to an operational stall (empty stall / unassigned protection)
        List<Map<String, Object>> horseRows = jdbc.queryForList(
                "select code, name, stable_location from horses where id=?", horseId);
        if (horseRows.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy thông tin ngựa: " + horseId);
        }
        String stableLocation = (String) horseRows.getFirst().get("stable_location");
        if (stableLocation == null || stableLocation.isBlank()) {
            throw new BusinessException("Ngựa chưa được phân bổ vào ô chuồng, không thể xác nhận cho ăn.");
        }

        // 3. Resolve effective nutrition plan for horse and date
        NutritionPlanResponse plan;
        try {
            plan = nutritionPlanService.getEffectivePlan(horseId, request.date());
        } catch (ResourceNotFoundException ex) {
            throw new BusinessException("Ngựa chưa có kế hoạch dinh dưỡng có hiệu lực, không thể xác nhận cho ăn.");
        }

        if (plan.meals() == null || plan.meals().isEmpty()) {
            throw new BusinessException("Kế hoạch dinh dưỡng của ngựa không có bữa ăn nào được thiết lập.");
        }

        // 4. Resolve meal and canonical meal key
        MealItem matchedMeal = resolveMeal(plan.meals(), request);
        String canonicalMealKey = matchedMeal.resolveIdentity();
        String canonicalMealName = matchedMeal.mealName().trim();
        LocalTime canonicalFeedingTime = matchedMeal.feedingTime();

        // 5. Idempotency check: if already confirmed for (horseId, date, mealKey), return existing
        Optional<DailyFeedingLogEntity> existing = repository.findByHorseIdAndFeedingDateAndMealKey(
                horseId, request.date(), canonicalMealKey);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        // Backward compatibility: check if historical record exists under legacy generated key
        String legacyKey = MealKeyGenerator.generate(matchedMeal.mealName(), matchedMeal.feedingTime());
        if (!legacyKey.equalsIgnoreCase(canonicalMealKey)) {
            Optional<DailyFeedingLogEntity> legacyExisting = repository.findByHorseIdAndFeedingDateAndMealKey(
                    horseId, request.date(), legacyKey);
            if (legacyExisting.isPresent()) {
                return toResponse(legacyExisting.get());
            }
        }

        // 6. Build immutable nutrient snapshot
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("mealKey", canonicalMealKey);
        snapshot.put("mealId", canonicalMealKey);
        snapshot.put("mealName", canonicalMealName);
        snapshot.put("feedingTime", canonicalFeedingTime.toString());
        snapshot.put("concentrateKg", matchedMeal.concentrateKg());
        snapshot.put("concentrateType", matchedMeal.concentrateType());
        snapshot.put("forageKg", matchedMeal.forageKg());
        snapshot.put("forageType", matchedMeal.forageType());
        snapshot.put("supplements", matchedMeal.supplements());

        String snapshotJson;
        try {
            snapshotJson = json.writeValueAsString(snapshot);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Không thể đóng gói dữ liệu bữa ăn", ex);
        }

        OffsetDateTime now = OffsetDateTime.now();
        DailyFeedingLogEntity entity = new DailyFeedingLogEntity(
                UUID.randomUUID(),
                horseId,
                request.date(),
                canonicalMealKey,
                canonicalMealName,
                canonicalFeedingTime,
                plan.id(),
                plan.version(),
                "CONFIRMED",
                actorUserId,
                now,
                snapshotJson,
                now
        );

        try {
            DailyFeedingLogEntity saved = repository.saveAndFlush(entity);
            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            // Concurrency guard: if another thread confirmed concurrently, return the persisted row
            return repository.findByHorseIdAndFeedingDateAndMealKey(horseId, request.date(), canonicalMealKey)
                    .map(this::toResponse)
                    .orElseThrow(() -> ex);
        }
    }

    @Transactional(readOnly = true)
    public List<DailyFeedingLogResponse> getHistory(UUID horseId, LocalDate startDate, LocalDate endDate) {
        horseAccess.requireActive(horseId);

        List<DailyFeedingLogEntity> entities;
        if (startDate != null && endDate != null) {
            entities = repository.findByHorseIdAndFeedingDateBetweenOrderByFeedingDateDescFeedingTimeDesc(
                    horseId, startDate, endDate);
        } else {
            entities = repository.findByHorseIdOrderByFeedingDateDescFeedingTimeDesc(horseId);
        }

        List<DailyFeedingLogResponse> list = new ArrayList<>(entities.size());
        for (DailyFeedingLogEntity e : entities) {
            list.add(toResponse(e));
        }
        return list;
    }

    private MealItem resolveMeal(List<MealItem> meals, ConfirmMealFeedingRequest request) {
        String reqKey = request.mealKey() != null ? request.mealKey().trim() : null;
        String reqName = request.mealName() != null ? request.mealName().trim() : null;
        LocalTime reqTime = request.feedingTime();

        // Pass 1: match by stable identity (id) or exact mealKey
        if (reqKey != null && !reqKey.isBlank()) {
            for (MealItem m : meals) {
                if (reqKey.equalsIgnoreCase(m.resolveIdentity()) ||
                        (m.id() != null && reqKey.equalsIgnoreCase(m.id()))) {
                    return m;
                }
            }
            // Legacy generated key or exact meal name match
            for (MealItem m : meals) {
                String generatedKey = MealKeyGenerator.generate(m.mealName(), m.feedingTime());
                if (generatedKey.equalsIgnoreCase(reqKey) || reqKey.equalsIgnoreCase(m.mealName().trim())) {
                    return m;
                }
            }
            // Also check slug
            for (MealItem m : meals) {
                String slug = MealKeyGenerator.slugify(m.mealName());
                if (slug.equalsIgnoreCase(reqKey)) {
                    return m;
                }
            }
        }

        // Pass 2: match by mealName and feedingTime
        if (reqName != null && !reqName.isBlank()) {
            for (MealItem m : meals) {
                if (m.mealName().trim().equalsIgnoreCase(reqName)) {
                    if (reqTime == null || m.feedingTime().equals(reqTime)) {
                        return m;
                    }
                }
            }
        }

        // Pass 3: match by feedingTime if unique
        if (reqTime != null) {
            List<MealItem> timeMatches = meals.stream()
                    .filter(m -> m.feedingTime().equals(reqTime))
                    .toList();
            if (timeMatches.size() == 1) {
                return timeMatches.getFirst();
            }
        }

        throw new BusinessException("Bữa ăn không tồn tại trong kế hoạch dinh dưỡng của ngựa: "
                + (reqKey != null ? reqKey : (reqName != null ? reqName : reqTime)));
    }

    public DailyFeedingLogResponse toResponse(DailyFeedingLogEntity entity) {
        String username = resolveUsername(entity.getConfirmedByUserId());
        Map<String, Object> snapshotMap = Collections.emptyMap();
        if (entity.getMealSnapshotJson() != null && !entity.getMealSnapshotJson().isBlank()) {
            try {
                snapshotMap = json.readValue(entity.getMealSnapshotJson(), new TypeReference<Map<String, Object>>() {});
            } catch (JacksonException ignored) {}
        }

        return new DailyFeedingLogResponse(
                entity.getId(),
                entity.getHorseId(),
                entity.getFeedingDate(),
                entity.getMealKey(),
                entity.getMealName(),
                entity.getFeedingTime(),
                entity.getPlanId(),
                entity.getPlanVersion(),
                entity.getStatus(),
                entity.getConfirmedByUserId(),
                username,
                entity.getConfirmedAt(),
                snapshotMap,
                entity.getCreatedAt()
        );
    }

    private String resolveUsername(UUID userId) {
        if (userId == null) return null;
        try {
            List<String> list = jdbc.query(
                    "select username from users where id=?",
                    (rs, rn) -> rs.getString("username"), userId);
            return list.isEmpty() ? userId.toString() : list.getFirst();
        } catch (Exception ex) {
            return userId.toString();
        }
    }
}
