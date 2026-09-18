package com.racehorse.health.service;

import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.health.dto.*;
import com.racehorse.horse.service.HorseAccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class HorseMeasurementService {
    private final JdbcTemplate jdbc;
    private final HorseAccessService access;
    public HorseMeasurementService(JdbcTemplate jdbc, HorseAccessService access) { this.jdbc=jdbc; this.access=access; }

    @Transactional
    public HorseWeightResponse addWeight(UUID horseId, UUID veterinarianId, CreateHorseWeightRequest request) {
        access.requireActive(horseId);
        UUID id=UUID.randomUUID();
        jdbc.update("insert into horse_weight_records(id,horse_id,measured_by_user_id,weight_kg,measured_at,notes) values(?,?,?,?,?,?)",
                id,horseId,veterinarianId,request.weightKg(),request.measuredAt(),request.notes());
        return new HorseWeightResponse(id,horseId,veterinarianId,request.weightKg(),request.measuredAt(),request.notes());
    }

    @Transactional(readOnly=true)
    public List<HorseWeightResponse> weights(UUID horseId, UUID userId, boolean privileged) {
        access.requireReadable(horseId,userId,privileged);
        return jdbc.query("select id,horse_id,measured_by_user_id,weight_kg,measured_at,notes from horse_weight_records where horse_id=? order by measured_at desc",
                this::weight,horseId);
    }

    @Transactional(readOnly=true)
    public RaceReadinessResponse readiness(UUID horseId, UUID raceEventId, UUID userId, boolean privileged) {
        access.requireReadable(horseId,userId,privileged);
        List<String> failed=new ArrayList<>();
        String health=firstString("select health_status from horses where id=?",horseId);
        if (!"FIT".equals(health)) failed.add("Trạng thái sức khỏe phải là FIT");
        Integer lockCount=jdbc.queryForObject("select count(*) from training_locks where horse_id=? and released_at is null",Integer.class,horseId);
        if (lockCount!=null && lockCount>0) failed.add("Ngựa đang bị khóa huấn luyện");
        String fitness=firstString("select top 1 fitness_status from training_evaluations where horse_id=? order by session_date desc,completed_at desc",horseId);
        if (!"GREEN".equals(fitness)) failed.add("Đánh giá thể lực gần nhất phải là GREEN");
        Integer restingHr=firstInteger("""
                select top 1 tr.heart_rate_bpm from telemetry_readings tr
                join telemetry_sessions ts on ts.id=tr.telemetry_session_id
                where ts.horse_id=? and ts.measurement_context='RESTING' and tr.heart_rate_bpm is not null
                order by tr.recorded_at desc
                """,horseId);
        if (restingHr==null || restingHr<30 || restingHr>42) failed.add("Nhịp tim nghỉ phải trong khoảng 30-42 bpm");
        BigDecimal weight=firstDecimal("select top 1 weight_kg from horse_weight_records where horse_id=? order by measured_at desc",horseId);
        if (weight==null) failed.add("Chưa có cân nặng do bác sĩ thú y nhập");
        if (raceEventId!=null) {
            List<Map<String,Object>> event=jdbc.queryForList("select minimum_weight_kg,maximum_weight_kg from race_events where id=? and is_active=1",raceEventId);
            if (event.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy giải đua: "+raceEventId);
            BigDecimal min=(BigDecimal)event.getFirst().get("minimum_weight_kg"), max=(BigDecimal)event.getFirst().get("maximum_weight_kg");
            if (weight!=null && min!=null && weight.compareTo(min)<0) failed.add("Cân nặng thấp hơn mức tối thiểu của giải");
            if (weight!=null && max!=null && weight.compareTo(max)>0) failed.add("Cân nặng cao hơn mức tối đa của giải");
        }
        return new RaceReadinessResponse(horseId,raceEventId,failed.isEmpty(),health,lockCount==0,fitness,restingHr,weight,List.copyOf(failed));
    }

    @Transactional
    public HealthStatusView updateHealthStatus(UUID horseId,UUID veterinarianId,UpdateHorseHealthStatusRequest request){
        access.requireActive(horseId);
        String status=request.status().trim().toUpperCase(Locale.ROOT);
        if(!Set.of("FIT","MONITOR","INJURED","QUARANTINED").contains(status))
            throw new com.racehorse.common.exception.BusinessException("status chỉ nhận FIT, MONITOR, INJURED hoặc QUARANTINED");
        String source=request.sourceType()==null||request.sourceType().isBlank()?"MANUAL":request.sourceType().trim().toUpperCase(Locale.ROOT);
        jdbc.update("update horses set health_status=?,health_changed_by_user_id=?,health_source_type=?,health_source_id=?,health_changed_at=SYSDATETIMEOFFSET(),updated_at=SYSDATETIMEOFFSET(),version=version+1 where id=?",
                status,veterinarianId,source,request.sourceId(),horseId);
        return healthStatus(horseId,veterinarianId,true);
    }

    @Transactional(readOnly=true)
    public HealthStatusView healthStatus(UUID horseId,UUID requester,boolean privileged){
        access.requireReadable(horseId,requester,privileged);
        return jdbc.query("select id,health_status,health_changed_by_user_id,health_source_type,health_source_id,health_changed_at from horses where id=?",
                rs->{if(!rs.next())throw new ResourceNotFoundException("Không tìm thấy ngựa: "+horseId);return new HealthStatusView(
                        rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3,UUID.class),rs.getString(4),
                        rs.getObject(5,UUID.class),rs.getObject(6,OffsetDateTime.class));},horseId);
    }

    private HorseWeightResponse weight(ResultSet rs,int row) throws SQLException { return new HorseWeightResponse(
            rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getBigDecimal(4),
            rs.getObject(5,OffsetDateTime.class),rs.getString(6)); }
    private String firstString(String sql,Object... args) { List<String> x=jdbc.query(sql,(r,n)->r.getString(1),args); return x.isEmpty()?null:x.getFirst(); }
    private Integer firstInteger(String sql,Object... args) { List<Integer> x=jdbc.query(sql,(r,n)->r.getObject(1,Integer.class),args); return x.isEmpty()?null:x.getFirst(); }
    private BigDecimal firstDecimal(String sql,Object... args) { List<BigDecimal> x=jdbc.query(sql,(r,n)->r.getBigDecimal(1),args); return x.isEmpty()?null:x.getFirst(); }
    public record HealthStatusView(UUID horseId,String status,UUID changedBy,String sourceType,UUID sourceId,OffsetDateTime changedAt){}
}
