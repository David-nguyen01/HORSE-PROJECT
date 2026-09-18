package com.racehorse.racing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.health.dto.RaceReadinessResponse;
import com.racehorse.health.service.HorseMeasurementService;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.racing.dto.CreateRaceEventRequest;
import com.racehorse.racing.dto.RaceEventResponse;
import com.racehorse.racing.dto.RaceRegistrationResponse;
import com.racehorse.racing.dto.RegisterHorseRequest;
import com.racehorse.racing.entity.RaceEvent;
import com.racehorse.racing.repository.RaceEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RaceEventService {

    private final RaceEventRepository repository;
    private final UserAccountRepository userAccountRepository;
    private final HorseMeasurementService readinessService;
    private final HorseAccessService horseAccessService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    private static final String RACING_MODULE = "RACING";
    private static final String REGISTRATION_TYPE = "RACE_REGISTRATIONS";

    public RaceEventService(RaceEventRepository repository,
                            UserAccountRepository userAccountRepository,
                            HorseMeasurementService readinessService,
                            HorseAccessService horseAccessService,
                            JdbcTemplate jdbc,
                            ObjectMapper mapper) {
        this.repository = repository;
        this.userAccountRepository = userAccountRepository;
        this.readinessService = readinessService;
        this.horseAccessService = horseAccessService;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public RaceEventResponse create(UUID userId, CreateRaceEventRequest request) {
        RaceEvent event = repository.save(new RaceEvent(
                request.raceName(),
                request.racecourse(),
                request.raceDate(),
                request.registrationDeadline(),
                request.distanceMeters(),
                request.raceClass(),
                request.minimumAgeYears(),
                request.maximumAgeYears(),
                request.minimumWeightKg(),
                request.maximumWeightKg(),
                userAccountRepository.getReferenceById(userId)
        ));
        return toResponse(event);
    }

    @Transactional
    public RaceEventResponse update(UUID id, CreateRaceEventRequest request) {
        RaceEvent event = find(id);
        event.update(
                request.raceName(),
                request.racecourse(),
                request.raceDate(),
                request.registrationDeadline(),
                request.distanceMeters(),
                request.raceClass(),
                request.minimumAgeYears(),
                request.maximumAgeYears(),
                request.minimumWeightKg(),
                request.maximumWeightKg()
        );
        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public Page<RaceEventResponse> getUpcoming(Pageable pageable) {
        return repository.findUpcoming(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RaceEventResponse getDetail(UUID id) {
        return toResponse(find(id));
    }

    @Transactional
    public RaceRegistrationResponse registerHorse(UUID raceEventId, UUID userId, boolean privileged, RegisterHorseRequest request) {
        RaceEvent event = find(raceEventId);
        if (!event.isActive()) {
            throw new BusinessException("Giải đua không còn hoạt động");
        }
        if (OffsetDateTime.now().isAfter(event.getRegistrationDeadline())) {
            throw new BusinessException("Đã quá hạn đăng ký cho giải đua này");
        }

        // Call single source of truth for readiness check
        RaceReadinessResponse readiness = readinessService.readiness(request.horseId(), raceEventId, userId, privileged);
        if (!readiness.ready()) {
            throw new BusinessException("Ngựa không đủ điều kiện đăng ký: " + String.join(", ", readiness.failedRules()));
        }

        // Deduplication check via module_records
        Integer exists = jdbc.queryForObject("""
                select count(*) from module_records 
                where module_code = ? and record_type = ? 
                  and horse_id = ? 
                  and json_value(data_json, '$.race_event_id') = ?
                  and status != 'WITHDRAWN'
                """, Integer.class, RACING_MODULE, REGISTRATION_TYPE, request.horseId(), raceEventId.toString());

        if (exists != null && exists > 0) {
            throw new BusinessException("Ngựa này đã được đăng ký hoặc đang chờ duyệt cho giải đua này");
        }

        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String json;
        try {
            json = mapper.writeValueAsString(Map.of(
                    "race_event_id", raceEventId.toString(),
                    "registered_by_user_id", userId.toString(),
                    "submitted_at", now.toString(),
                    "confirmed_at", now.toString(), // Auto-confirming for now based on FR18 unless specified otherwise
                    "registered_weight_kg", readiness.weightKg()
            ));
        } catch (JsonProcessingException e) {
            throw new BusinessException("Lỗi lưu dữ liệu đăng ký");
        }

        try {
            jdbc.update("""
                    insert into module_records (
                        id, module_code, record_type, horse_id, user_id, status, event_date, data_json, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, cast(? as date), ?, ?, ?)
                    """, id, RACING_MODULE, REGISTRATION_TYPE, request.horseId(), userId, "CONFIRMED", event.getRaceDate(), json, now, now);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new BusinessException("Ngựa này đã được đăng ký hoặc đang chờ duyệt cho giải đua này");
        }

        return new RaceRegistrationResponse(id, raceEventId, request.horseId(), userId, "CONFIRMED", now, now, null, null);
    }

    @Transactional
    public void withdrawRegistration(UUID raceEventId, UUID registrationId, UUID userId) {
        RaceEvent event = find(raceEventId);
        if (OffsetDateTime.now().isAfter(event.getRegistrationDeadline())) {
            throw new BusinessException("Đã quá hạn rút lui đăng ký cho giải đua này");
        }

        List<Map<String, Object>> record = jdbc.queryForList("""
                select data_json, status, horse_id from module_records
                where id = ? and module_code = ? and record_type = ?
                """, registrationId, RACING_MODULE, REGISTRATION_TYPE);

        if (record.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy đăng ký: " + registrationId);
        }
        
        if ("WITHDRAWN".equals(record.getFirst().get("status"))) {
            throw new BusinessException("Đăng ký này đã bị rút lui");
        }

        try {
            Map<String, Object> data = mapper.readValue((String) record.getFirst().get("data_json"), Map.class);
            data.put("withdrawn_at", OffsetDateTime.now().toString());
            data.put("withdrawn_by_user_id", userId.toString());
            
            jdbc.update("""
                    update module_records
                    set status = 'WITHDRAWN', data_json = ?, updated_at = ?
                    where id = ?
                    """, mapper.writeValueAsString(data), OffsetDateTime.now(), registrationId);
        } catch (Exception e) {
            throw new BusinessException("Lỗi xử lý dữ liệu json");
        }
    }

    @Transactional(readOnly = true)
    public List<RaceRegistrationResponse> getRegistrations(UUID raceEventId) {
        return jdbc.query("""
                select id, horse_id, user_id, status, data_json
                from module_records
                where module_code = ? and record_type = ?
                  and json_value(data_json, '$.race_event_id') = ?
                order by created_at desc
                """, (rs, rowNum) -> {
            try {
                Map<String, Object> data = mapper.readValue(rs.getString("data_json"), Map.class);
                return new RaceRegistrationResponse(
                        rs.getObject("id", UUID.class),
                        raceEventId,
                        rs.getObject("horse_id", UUID.class),
                        UUID.fromString((String) data.get("registered_by_user_id")),
                        rs.getString("status"),
                        OffsetDateTime.parse((String) data.get("submitted_at")),
                        data.containsKey("confirmed_at") ? OffsetDateTime.parse((String) data.get("confirmed_at")) : null,
                        data.containsKey("withdrawn_at") ? OffsetDateTime.parse((String) data.get("withdrawn_at")) : null,
                        data.containsKey("withdrawn_by_user_id") ? UUID.fromString((String) data.get("withdrawn_by_user_id")) : null
                );
            } catch (Exception e) {
                return null;
            }
        }, RACING_MODULE, REGISTRATION_TYPE, raceEventId.toString()).stream().filter(r -> r != null).collect(Collectors.toList());
    }

    private RaceEvent find(UUID id) {
        return repository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giải đua: " + id));
    }

    private RaceEventResponse toResponse(RaceEvent event) {
        return new RaceEventResponse(
                event.getId(),
                event.getRaceName(),
                event.getRacecourse(),
                event.getRaceDate(),
                event.getRegistrationDeadline(),
                event.getDistanceMeters(),
                event.getRaceClass(),
                event.getMinimumAgeYears(),
                event.getMaximumAgeYears(),
                event.getMinimumWeightKg(),
                event.getMaximumWeightKg(),
                event.getCreatedBy().getId(),
                event.isActive()
        );
    }
}
