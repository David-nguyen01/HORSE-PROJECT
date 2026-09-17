package com.racehorse.racing.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.health.dto.RaceReadinessResponse;
import com.racehorse.health.service.HorseMeasurementService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class RaceEventService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final HorseMeasurementService measurement;

    public RaceEventService(JdbcTemplate jdbc, ObjectMapper json, HorseMeasurementService measurement) {
        this.jdbc = jdbc; this.json = json; this.measurement = measurement;
    }

    @Transactional
    public EventView create(UUID actor, EventCommand command) {
        validate(command);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into race_events(id,race_name,racecourse,race_date,registration_deadline,distance_meters,race_class,minimum_age_years,maximum_age_years,minimum_weight_kg,maximum_weight_kg,created_by_user_id,is_active) "
                        + "values(?,?,?,?,?,?,?,?,?,?,?,?,1)", id, required(command.raceName()), required(command.racecourse()),
                command.raceDate(), command.registrationDeadline(), command.distanceMeters(), required(command.raceClass()),
                command.minimumAgeYears(), command.maximumAgeYears(), command.minimumWeightKg(), command.maximumWeightKg(), actor);
        return event(id);
    }

    @Transactional
    public EventView update(UUID id, EventCommand command) {
        event(id); validate(command);
        jdbc.update("update race_events set race_name=?,racecourse=?,race_date=?,registration_deadline=?,distance_meters=?,race_class=?,minimum_age_years=?,maximum_age_years=?,minimum_weight_kg=?,maximum_weight_kg=?,is_active=?,updated_at=SYSDATETIMEOFFSET() where id=?",
                required(command.raceName()), required(command.racecourse()), command.raceDate(), command.registrationDeadline(),
                command.distanceMeters(), required(command.raceClass()), command.minimumAgeYears(), command.maximumAgeYears(),
                command.minimumWeightKg(), command.maximumWeightKg(), command.active(), id);
        return event(id);
    }

    @Transactional
    public void deactivate(UUID id) {
        event(id);
        jdbc.update("update race_events set is_active=0,updated_at=SYSDATETIMEOFFSET() where id=?", id);
    }

    @Transactional(readOnly = true)
    public EventView event(UUID id) {
        List<EventView> rows = jdbc.query("select * from race_events where id=?", (rs,row)->event(rs,row), id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy giải đua: " + id);
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public List<EventView> events(LocalDate from, LocalDate to, Boolean active) {
        StringBuilder sql = new StringBuilder("select * from race_events where 1=1");
        List<Object> args = new ArrayList<>();
        if (from != null) { sql.append(" and race_date>=?"); args.add(from); }
        if (to != null) { sql.append(" and race_date<=?"); args.add(to); }
        if (active != null) { sql.append(" and is_active=?"); args.add(active); }
        sql.append(" order by race_date,race_name");
        return jdbc.query(sql.toString(), (rs,row)->event(rs,row), args.toArray());
    }

    @Transactional
    public RegistrationView register(UUID eventId, UUID horseId, UUID actor, BigDecimal registeredWeight) {
        EventView event = event(eventId);
        if (!event.active()) throw new BusinessException("Giải đua đã ngừng nhận đăng ký");
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isAfter(event.registrationDeadline())) throw new BusinessException("Đã quá hạn đăng ký giải đua");
        Integer duplicate = jdbc.queryForObject("select count(*) from module_records where module_code='RACING' "
                + "and record_type='RACE_REGISTRATION' and horse_id=? and JSON_VALUE(data_json,'$.raceEventId')=?",
                Integer.class, horseId, eventId.toString());
        if (duplicate != null && duplicate > 0) throw new BusinessException("Ngựa đã được đăng ký cho giải này");
        validateAge(event, horseId);
        RaceReadinessResponse readiness = measurement.readiness(horseId, eventId, actor, true);
        if (!readiness.ready()) throw new BusinessException("Ngựa chưa sẵn sàng dự đua: " + String.join("; ", readiness.failedRules()));
        BigDecimal weight = registeredWeight == null ? readiness.latestWeightKg() : registeredWeight;
        UUID id = UUID.randomUUID();
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("raceEventId", eventId.toString()); data.put("registeredWeightKg", weight);
        data.put("submittedAt", now.toString()); data.put("confirmedAt", null);
        data.put("withdrawnAt", null); data.put("withdrawnByUserId", null);
        jdbc.update("insert into module_records(id,module_code,record_type,source_table,horse_id,user_id,status,event_date,data_json) "
                        + "values(?,'RACING','RACE_REGISTRATION','api',?,?,'SUBMITTED',?,?)",
                id, horseId, actor, event.raceDate(), toJson(data));
        return registration(id);
    }

    @Transactional
    public RegistrationView confirm(UUID registrationId) {
        RegistrationView value = registration(registrationId);
        if (!"SUBMITTED".equals(value.status())) throw new BusinessException("Chỉ đăng ký SUBMITTED mới có thể xác nhận");
        String at = OffsetDateTime.now().toString();
        jdbc.update("update module_records set status='CONFIRMED',data_json=JSON_MODIFY(data_json,'$.confirmedAt',?),updated_at=SYSDATETIMEOFFSET() where id=?",
                at, registrationId);
        return registration(registrationId);
    }

    @Transactional
    public RegistrationView withdraw(UUID registrationId, UUID actor) {
        RegistrationView value = registration(registrationId);
        if ("WITHDRAWN".equals(value.status())) throw new BusinessException("Đăng ký đã được rút");
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("update module_records set status='WITHDRAWN',data_json=JSON_MODIFY(JSON_MODIFY(data_json,'$.withdrawnAt',?),'$.withdrawnByUserId',?),updated_at=SYSDATETIMEOFFSET() where id=?",
                now.toString(), actor.toString(), registrationId);
        return registration(registrationId);
    }

    @Transactional(readOnly = true)
    public List<RegistrationView> registrations(UUID eventId, UUID horseId, String status) {
        StringBuilder sql = new StringBuilder(registrationSelect()).append(" where m.module_code='RACING' and m.record_type='RACE_REGISTRATION'");
        List<Object> args = new ArrayList<>();
        if (eventId != null) { sql.append(" and JSON_VALUE(m.data_json,'$.raceEventId')=?"); args.add(eventId.toString()); }
        if (horseId != null) { sql.append(" and m.horse_id=?"); args.add(horseId); }
        if (status != null && !status.isBlank()) { sql.append(" and m.status=?"); args.add(status.trim().toUpperCase(Locale.ROOT)); }
        sql.append(" order by m.created_at desc");
        return jdbc.query(sql.toString(), (rs,row)->registration(rs,row), args.toArray());
    }

    @Transactional(readOnly = true)
    public RegistrationView registration(UUID id) {
        List<RegistrationView> rows = jdbc.query(registrationSelect() + " where m.id=? and m.record_type='RACE_REGISTRATION'",
                (rs,row)->registration(rs,row), id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy đăng ký đua: " + id);
        return rows.getFirst();
    }

    private String registrationSelect() {
        return "select m.id,m.horse_id,h.name horse_name,m.user_id,m.status,m.data_json,e.race_name,e.race_date "
                + "from module_records m join horses h on h.id=m.horse_id join race_events e on cast(e.id as varchar(36))=JSON_VALUE(m.data_json,'$.raceEventId')";
    }

    private RegistrationView registration(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        JsonNode node = read(rs.getString("data_json"));
        return new RegistrationView(rs.getObject("id", UUID.class), UUID.fromString(node.path("raceEventId").asText()),
                rs.getString("race_name"), rs.getObject("race_date", LocalDate.class), rs.getObject("horse_id", UUID.class),
                rs.getString("horse_name"), rs.getObject("user_id", UUID.class), rs.getString("status"),
                node.path("registeredWeightKg").decimalValue(), parseTime(node, "submittedAt"),
                parseTime(node, "confirmedAt"), parseTime(node, "withdrawnAt"),
                uuidOrNull(node.path("withdrawnByUserId").asText(null)));
    }

    private EventView event(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new EventView(rs.getObject("id", UUID.class), rs.getString("race_name"), rs.getString("racecourse"),
                rs.getObject("race_date", LocalDate.class), rs.getObject("registration_deadline", OffsetDateTime.class),
                rs.getBigDecimal("distance_meters"), rs.getString("race_class"),
                rs.getObject("minimum_age_years", Integer.class), rs.getObject("maximum_age_years", Integer.class),
                rs.getBigDecimal("minimum_weight_kg"), rs.getBigDecimal("maximum_weight_kg"), rs.getBoolean("is_active"));
    }

    private void validate(EventCommand c) {
        if (c.raceDate() == null || c.registrationDeadline() == null || c.distanceMeters() == null)
            throw new BusinessException("Thiếu thời gian hoặc cự ly giải đua");
        if (c.distanceMeters().signum() <= 0) throw new BusinessException("Cự ly phải lớn hơn 0");
        if (c.registrationDeadline().toLocalDate().isAfter(c.raceDate())) throw new BusinessException("Hạn đăng ký không được sau ngày đua");
        if (c.minimumAgeYears() != null && c.maximumAgeYears() != null && c.minimumAgeYears() > c.maximumAgeYears())
            throw new BusinessException("Khoảng tuổi không hợp lệ");
        if (c.minimumWeightKg() != null && c.maximumWeightKg() != null && c.minimumWeightKg().compareTo(c.maximumWeightKg()) > 0)
            throw new BusinessException("Khoảng cân nặng không hợp lệ");
    }

    private void validateAge(EventView event, UUID horseId) {
        LocalDate birth = jdbc.queryForObject("select birth_date from horses where id=?", LocalDate.class, horseId);
        if (birth == null) throw new ResourceNotFoundException("Không tìm thấy ngựa: " + horseId);
        int age = Period.between(birth, event.raceDate()).getYears();
        if (event.minimumAgeYears() != null && age < event.minimumAgeYears()) throw new BusinessException("Ngựa chưa đủ tuổi dự giải");
        if (event.maximumAgeYears() != null && age > event.maximumAgeYears()) throw new BusinessException("Ngựa vượt quá tuổi của giải");
    }

    private JsonNode read(String value) { try { return json.readTree(value); } catch (JacksonException e) { throw new IllegalStateException(e); } }
    private String toJson(Object value) { try { return json.writeValueAsString(value); } catch (JacksonException e) { throw new IllegalStateException(e); } }
    private OffsetDateTime parseTime(JsonNode node, String field) { String value=node.path(field).asText(null); return value==null||value.isBlank()?null:OffsetDateTime.parse(value); }
    private UUID uuidOrNull(String value) { return value == null || value.isBlank() ? null : UUID.fromString(value); }
    private String required(String value) { if (value == null || value.isBlank()) throw new BusinessException("Dữ liệu giải đua không được để trống"); return value.trim(); }

    public record EventCommand(String raceName, String racecourse, LocalDate raceDate, OffsetDateTime registrationDeadline,
                               BigDecimal distanceMeters, String raceClass, Integer minimumAgeYears, Integer maximumAgeYears,
                               BigDecimal minimumWeightKg, BigDecimal maximumWeightKg, boolean active) {}
    public record EventView(UUID id, String raceName, String racecourse, LocalDate raceDate,
                            OffsetDateTime registrationDeadline, BigDecimal distanceMeters, String raceClass,
                            Integer minimumAgeYears, Integer maximumAgeYears, BigDecimal minimumWeightKg,
                            BigDecimal maximumWeightKg, boolean active) {}
    public record RegistrationView(UUID id, UUID raceEventId, String raceName, LocalDate raceDate,
                                   UUID horseId, String horseName, UUID registeredBy, String status,
                                   BigDecimal registeredWeightKg, OffsetDateTime submittedAt,
                                   OffsetDateTime confirmedAt, OffsetDateTime withdrawnAt, UUID withdrawnBy) {}
}
