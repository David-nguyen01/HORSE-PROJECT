package com.racehorse.training.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.training.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class TelemetryService {
    private final JdbcTemplate jdbc;
    private final HorseAccessService access;
    public TelemetryService(JdbcTemplate jdbc,HorseAccessService access){this.jdbc=jdbc;this.access=access;}

    @Transactional
    public UUID configureThreshold(UUID horseId,UUID actor,CreateMetricThresholdRequest r){
        access.requireActive(horseId);
        String metric=upper(r.metricType());
        if(!List.of("HEART_RATE","SPEED").contains(metric)) throw new BusinessException("metricType chỉ nhận HEART_RATE hoặc SPEED");
        if(r.minimumValue()==null&&r.maximumValue()==null) throw new BusinessException("Phải nhập ít nhất một ngưỡng");
        if(r.minimumValue()!=null&&r.maximumValue()!=null&&r.minimumValue().compareTo(r.maximumValue())>=0) throw new BusinessException("Ngưỡng tối thiểu phải nhỏ hơn tối đa");
        UUID id=UUID.randomUUID();
        jdbc.update("insert into horse_metric_thresholds(id,horse_id,metric_type,minimum_value,maximum_value,unit,configured_by_user_id,effective_from,effective_to) values(?,?,?,?,?,?,?,?,?)",
                id,horseId,metric,r.minimumValue(),r.maximumValue(),r.unit(),actor,r.effectiveFrom(),r.effectiveTo());
        return id;
    }

    @Transactional
    public TelemetryResponse enter(UUID horseId,UUID actor,CreateTelemetryRequest r){
        access.requireActive(horseId);
        String context=upper(r.measurementContext());
        if(!List.of("RESTING","TRAINING","RECOVERY").contains(context)) throw new BusinessException("measurementContext không hợp lệ");
        if(r.endedAt()!=null&&!r.startedAt().isBefore(r.endedAt())) throw new BusinessException("endedAt phải sau startedAt");
        if(r.trainingScheduleId()!=null){
            Integer count=jdbc.queryForObject("select count(*) from training_schedules where id=? and horse_id=?",Integer.class,r.trainingScheduleId(),horseId);
            if(count==null||count==0) throw new BusinessException("Lịch tập không thuộc ngựa này");
        }
        UUID session=UUID.randomUUID();
        jdbc.update("insert into telemetry_sessions(id,horse_id,training_schedule_id,started_at,ended_at,recorded_by_user_id,entry_method,measurement_context) values(?,?,?,?,?,?,'MANUAL',?)",
                session,horseId,r.trainingScheduleId(),r.startedAt(),r.endedAt(),actor,context);
        List<TelemetryResponse.Alert> alerts=new ArrayList<>();
        for(CreateTelemetryRequest.Reading reading:r.readings()){
            if(reading.heartRateBpm()==null&&reading.speedKmh()==null) throw new BusinessException("Mỗi dòng phải có nhịp tim hoặc tốc độ");
            long readingId=insertReading(session,reading);
            if(reading.heartRateBpm()!=null) evaluate(horseId,session,readingId,"HEART_RATE",BigDecimal.valueOf(reading.heartRateBpm()),reading.recordedAt(),alerts);
            if(reading.speedKmh()!=null) evaluate(horseId,session,readingId,"SPEED",reading.speedKmh(),reading.recordedAt(),alerts);
        }
        return new TelemetryResponse(session,horseId,"MANUAL",context,r.startedAt(),r.endedAt(),r.readings().size(),alerts);
    }

    private long insertReading(UUID session,CreateTelemetryRequest.Reading r){
        GeneratedKeyHolder keys=new GeneratedKeyHolder();
        jdbc.update(c->{PreparedStatement ps=c.prepareStatement("insert into telemetry_readings(telemetry_session_id,recorded_at,heart_rate_bpm,speed_kmh) values(?,?,?,?)",Statement.RETURN_GENERATED_KEYS);ps.setObject(1,session);ps.setObject(2,r.recordedAt());ps.setObject(3,r.heartRateBpm());ps.setObject(4,r.speedKmh());return ps;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private void evaluate(UUID horse,UUID session,long readingId,String metric,BigDecimal value,OffsetDateTime at,List<TelemetryResponse.Alert> out){
        List<Map<String,Object>> rows=jdbc.queryForList("""
                select top 1 id,minimum_value,maximum_value from horse_metric_thresholds
                where horse_id=? and metric_type=? and effective_from<=? and (effective_to is null or effective_to>?)
                order by effective_from desc
                """,horse,metric,at,at);
        if(rows.isEmpty()) return;
        Map<String,Object> t=rows.getFirst(); UUID threshold=uuid(t.get("id"));
        BigDecimal min=(BigDecimal)t.get("minimum_value"),max=(BigDecimal)t.get("maximum_value");
        if(min!=null&&value.compareTo(min)<0) alert(horse,session,readingId,threshold,metric,value,min,"MINIMUM",at,out);
        if(max!=null&&value.compareTo(max)>0) alert(horse,session,readingId,threshold,metric,value,max,"MAXIMUM",at,out);
    }
    private void alert(UUID horse,UUID session,long readingId,UUID threshold,String metric,BigDecimal observed,BigDecimal limit,String direction,OffsetDateTime at,List<TelemetryResponse.Alert> out){
        UUID id=UUID.randomUUID(); String message=metric+" = "+observed+" vượt ngưỡng "+direction+" = "+limit;
        jdbc.update("insert into alert_events(id,horse_id,telemetry_session_id,telemetry_reading_id,threshold_id,metric_type,observed_value,threshold_value,threshold_direction,occurred_at) values(?,?,?,?,?,?,?,?,?,?)",
                id,horse,session,readingId,threshold,metric,observed,limit,direction,at);
        jdbc.update("""
                insert into notifications(id,recipient_user_id,notification_type,priority,title,message,related_entity_type,related_entity_id,deduplication_key)
                select newid(),ur.user_id,'FITNESS_THRESHOLD','HIGH',N'Cảnh báo chỉ số ngựa',?,'ALERT_EVENT',?,concat('alert:',cast(? as varchar(36)),':',cast(ur.user_id as varchar(36)))
                from user_roles ur where ur.role='HEAD_TRAINER'
                """,message,id,id);
        out.add(new TelemetryResponse.Alert(id,metric,direction,message));
    }
    private String upper(String value){return value.trim().toUpperCase(Locale.ROOT);}
    private UUID uuid(Object value){return value instanceof UUID id?id:UUID.fromString(String.valueOf(value));}
}
