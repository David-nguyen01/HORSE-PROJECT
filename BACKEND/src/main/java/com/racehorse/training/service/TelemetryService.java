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
<<<<<<< Updated upstream
        if(!List.of("HEART_RATE","SPEED").contains(metric)) throw new BusinessException("metricType chỉ nhận HEART_RATE hoặc SPEED");
        if(r.minimumValue()==null&&r.maximumValue()==null) throw new BusinessException("Phải nhập ít nhất một ngưỡng");
        if(r.minimumValue()!=null&&r.maximumValue()!=null&&r.minimumValue().compareTo(r.maximumValue())>=0) throw new BusinessException("Ngưỡng tối thiểu phải nhỏ hơn tối đa");
=======
        if(!List.of("HEART_RATE","SPEED").contains(metric)) throw new InvalidInputException("metricType chỉ nhận HEART_RATE hoặc SPEED");
        if(r.minimumValue()==null&&r.maximumValue()==null) throw new InvalidInputException("Phải nhập ít nhất một ngưỡng");
        if(r.minimumValue()!=null&&r.maximumValue()!=null&&r.minimumValue().compareTo(r.maximumValue())>=0) throw new InvalidInputException("Ngưỡng tối thiểu phải nhỏ hơn tối đa");
        if(r.effectiveFrom()!=null&&r.effectiveTo()!=null&&!r.effectiveFrom().isBefore(r.effectiveTo())) throw new InvalidInputException("effectiveTo phải sau effectiveFrom");
>>>>>>> Stashed changes
        UUID id=UUID.randomUUID();
        jdbc.update("insert into horse_metric_thresholds(id,horse_id,metric_type,minimum_value,maximum_value,unit,configured_by_user_id,effective_from,effective_to) values(?,?,?,?,?,?,?,?,?)",
                id,horseId,metric,r.minimumValue(),r.maximumValue(),r.unit(),actor,r.effectiveFrom(),r.effectiveTo());
        return id;
    }

    @Transactional(readOnly = true)
    public List<ThresholdView> thresholds(UUID horseId, UUID requester, boolean privileged, boolean includeExpired) {
        access.requireReadable(horseId, requester, privileged);
        String sql="select id,horse_id,metric_type,minimum_value,maximum_value,unit,configured_by_user_id,effective_from,effective_to,created_at "
                +"from horse_metric_thresholds where horse_id=? "+(includeExpired?"":"and (effective_to is null or effective_to>SYSDATETIMEOFFSET()) ")
                +"order by metric_type,effective_from desc";
        return jdbc.query(sql,(rs,row)->new ThresholdView(rs.getObject("id",UUID.class),rs.getObject("horse_id",UUID.class),
                rs.getString("metric_type"),rs.getBigDecimal("minimum_value"),rs.getBigDecimal("maximum_value"),
                rs.getString("unit"),rs.getObject("configured_by_user_id",UUID.class),
                rs.getObject("effective_from",OffsetDateTime.class),rs.getObject("effective_to",OffsetDateTime.class),
                rs.getObject("created_at",OffsetDateTime.class)),horseId);
    }

    @Transactional
    public ThresholdView updateThreshold(UUID horseId, UUID thresholdId, UUID actor, CreateMetricThresholdRequest r) {
        access.requireActive(horseId);
        Integer count=jdbc.queryForObject("select count(*) from horse_metric_thresholds where id=? and horse_id=?",Integer.class,thresholdId,horseId);
        if(count==null||count==0) throw new com.racehorse.common.exception.ResourceNotFoundException("Không tìm thấy ngưỡng: "+thresholdId);
        String metric=upper(r.metricType());
<<<<<<< Updated upstream
        if(!List.of("HEART_RATE","SPEED").contains(metric)) throw new BusinessException("metricType chỉ nhận HEART_RATE hoặc SPEED");
        if(r.minimumValue()==null&&r.maximumValue()==null) throw new BusinessException("Phải nhập ít nhất một ngưỡng");
        if(r.minimumValue()!=null&&r.maximumValue()!=null&&r.minimumValue().compareTo(r.maximumValue())>=0) throw new BusinessException("Ngưỡng tối thiểu phải nhỏ hơn tối đa");
=======
        if(!List.of("HEART_RATE","SPEED").contains(metric)) throw new InvalidInputException("metricType chỉ nhận HEART_RATE hoặc SPEED");
        if(r.minimumValue()==null&&r.maximumValue()==null) throw new InvalidInputException("Phải nhập ít nhất một ngưỡng");
        if(r.minimumValue()!=null&&r.maximumValue()!=null&&r.minimumValue().compareTo(r.maximumValue())>=0) throw new InvalidInputException("Ngưỡng tối thiểu phải nhỏ hơn tối đa");
        if(r.effectiveFrom()!=null&&r.effectiveTo()!=null&&!r.effectiveFrom().isBefore(r.effectiveTo())) throw new InvalidInputException("effectiveTo phải sau effectiveFrom");
>>>>>>> Stashed changes
        jdbc.update("update horse_metric_thresholds set metric_type=?,minimum_value=?,maximum_value=?,unit=?,configured_by_user_id=?,effective_from=?,effective_to=? where id=?",
                metric,r.minimumValue(),r.maximumValue(),r.unit(),actor,r.effectiveFrom(),r.effectiveTo(),thresholdId);
        return thresholds(horseId,actor,true,true).stream().filter(v->v.id().equals(thresholdId)).findFirst().orElseThrow();
    }

    @Transactional
    public void endThreshold(UUID horseId, UUID thresholdId) {
        int changed=jdbc.update("update horse_metric_thresholds set effective_to=case when effective_to is null or effective_to>SYSDATETIMEOFFSET() then SYSDATETIMEOFFSET() else effective_to end where id=? and horse_id=?",
                thresholdId,horseId);
        if(changed==0) throw new com.racehorse.common.exception.ResourceNotFoundException("Không tìm thấy ngưỡng: "+thresholdId);
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

    @Transactional(readOnly = true)
    public List<SessionView> history(UUID horseId, UUID requester, boolean privileged, String context,
                                     OffsetDateTime from, OffsetDateTime to, int page, int size) {
        access.requireReadable(horseId,requester,privileged);
        if(page<0||size<1||size>200) throw new BusinessException("Phân trang không hợp lệ");
        StringBuilder sql=new StringBuilder("select id,training_schedule_id,started_at,ended_at,recorded_by_user_id,entry_method,measurement_context from telemetry_sessions where horse_id=?");
        List<Object> args=new ArrayList<>();args.add(horseId);
        if(context!=null&&!context.isBlank()){sql.append(" and measurement_context=?");args.add(upper(context));}
        if(from!=null){sql.append(" and started_at>=?");args.add(from);}
        if(to!=null){sql.append(" and started_at<=?");args.add(to);}
        sql.append(" order by started_at desc offset ? rows fetch next ? rows only");args.add(page*size);args.add(size);
        return jdbc.query(sql.toString(),(rs,row)->{
            UUID sessionId=rs.getObject("id",UUID.class);
            List<ReadingView> readings=jdbc.query("select id,recorded_at,heart_rate_bpm,speed_kmh from telemetry_readings where telemetry_session_id=? order by recorded_at",
                    (rr,n)->new ReadingView(rr.getLong("id"),rr.getObject("recorded_at",OffsetDateTime.class),
                            rr.getObject("heart_rate_bpm",Integer.class),rr.getBigDecimal("speed_kmh")),sessionId);
            return new SessionView(sessionId,horseId,rs.getObject("training_schedule_id",UUID.class),
                    rs.getObject("recorded_by_user_id",UUID.class),rs.getString("entry_method"),
                    rs.getString("measurement_context"),rs.getObject("started_at",OffsetDateTime.class),
                    rs.getObject("ended_at",OffsetDateTime.class),readings);
        },args.toArray());
    }

    @Transactional(readOnly = true)
    public List<AlertView> alerts(UUID horseId, UUID requester, boolean privileged, String metric, Boolean acknowledged,
                                  OffsetDateTime from, OffsetDateTime to, int page, int size) {
        access.requireReadable(horseId,requester,privileged);
        if(page<0||size<1||size>200) throw new BusinessException("Phân trang không hợp lệ");
        StringBuilder sql=new StringBuilder("select id,telemetry_session_id,telemetry_reading_id,threshold_id,metric_type,observed_value,threshold_value,threshold_direction,occurred_at,acknowledged_by_user_id,acknowledged_at from alert_events where horse_id=?");
        List<Object> args=new ArrayList<>();args.add(horseId);
        if(metric!=null&&!metric.isBlank()){sql.append(" and metric_type=?");args.add(upper(metric));}
        if(acknowledged!=null)sql.append(acknowledged?" and acknowledged_at is not null":" and acknowledged_at is null");
        if(from!=null){sql.append(" and occurred_at>=?");args.add(from);}
        if(to!=null){sql.append(" and occurred_at<=?");args.add(to);}
        sql.append(" order by occurred_at desc offset ? rows fetch next ? rows only");args.add(page*size);args.add(size);
        return jdbc.query(sql.toString(),(rs,row)->new AlertView(rs.getObject("id",UUID.class),horseId,
                rs.getObject("telemetry_session_id",UUID.class),rs.getLong("telemetry_reading_id"),
                rs.getObject("threshold_id",UUID.class),rs.getString("metric_type"),rs.getBigDecimal("observed_value"),
                rs.getBigDecimal("threshold_value"),rs.getString("threshold_direction"),
                rs.getObject("occurred_at",OffsetDateTime.class),rs.getObject("acknowledged_by_user_id",UUID.class),
                rs.getObject("acknowledged_at",OffsetDateTime.class)),args.toArray());
    }

    @Transactional
    public AlertView acknowledge(UUID horseId, UUID alertId, UUID actor) {
        int changed=jdbc.update("update alert_events set acknowledged_by_user_id=?,acknowledged_at=SYSDATETIMEOFFSET() where id=? and horse_id=? and acknowledged_at is null",
                actor,alertId,horseId);
        if(changed==0){
            Integer exists=jdbc.queryForObject("select count(*) from alert_events where id=? and horse_id=?",Integer.class,alertId,horseId);
            if(exists==null||exists==0)throw new com.racehorse.common.exception.ResourceNotFoundException("Không tìm thấy cảnh báo: "+alertId);
        }
<<<<<<< Updated upstream
        return alerts(horseId,actor,true,null,null,null,null,0,200).stream().filter(v->v.id().equals(alertId)).findFirst().orElseThrow();
=======
        List<AlertView> rows = jdbc.query(
                "select id,telemetry_session_id,telemetry_reading_id,threshold_id,metric_type,observed_value,threshold_value,threshold_direction,occurred_at,acknowledged_by_user_id,acknowledged_at from alert_events where id=? and horse_id=?",
                (rs, row) -> new AlertView(rs.getObject("id", UUID.class), horseId,
                        rs.getObject("telemetry_session_id", UUID.class), rs.getLong("telemetry_reading_id"),
                        rs.getObject("threshold_id", UUID.class), rs.getString("metric_type"), rs.getBigDecimal("observed_value"),
                        rs.getBigDecimal("threshold_value"), rs.getString("threshold_direction"),
                        rs.getObject("occurred_at", OffsetDateTime.class), rs.getObject("acknowledged_by_user_id", UUID.class),
                        rs.getObject("acknowledged_at", OffsetDateTime.class)), alertId, horseId);
        if (rows.isEmpty()) throw new com.racehorse.common.exception.ResourceNotFoundException("Không tìm thấy cảnh báo: " + alertId);
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public List<SessionView> history(UUID horseId, UUID requester, boolean manager, boolean trainer, boolean owner,
                                     boolean vet, String context, OffsetDateTime from, OffsetDateTime to, int page, int size) {
        requireAccess(horseId,requester,manager,trainer,owner,vet);
        return historyAfterAccess(horseId, context, from, to, page, size);
    }

    @Transactional(readOnly = true)
    public List<AlertView> alerts(UUID horseId, UUID requester, boolean manager, boolean trainer, boolean owner,
                                  boolean vet, String metric, Boolean acknowledged, OffsetDateTime from,
                                  OffsetDateTime to, int page, int size) {
        requireAccess(horseId,requester,manager,trainer,owner,vet);
        return alertsAfterAccess(horseId, metric, acknowledged, from, to, page, size);
>>>>>>> Stashed changes
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

    public record ThresholdView(UUID id,UUID horseId,String metricType,BigDecimal minimumValue,BigDecimal maximumValue,
                                String unit,UUID configuredBy,OffsetDateTime effectiveFrom,OffsetDateTime effectiveTo,
                                OffsetDateTime createdAt){}
    public record ReadingView(long id,OffsetDateTime recordedAt,Integer heartRateBpm,BigDecimal speedKmh){}
    public record SessionView(UUID id,UUID horseId,UUID trainingScheduleId,UUID recordedBy,String entryMethod,
                              String measurementContext,OffsetDateTime startedAt,OffsetDateTime endedAt,
                              List<ReadingView> readings){}
    public record AlertView(UUID id,UUID horseId,UUID telemetrySessionId,long telemetryReadingId,UUID thresholdId,
                            String metricType,BigDecimal observedValue,BigDecimal thresholdValue,String direction,
                            OffsetDateTime occurredAt,UUID acknowledgedBy,OffsetDateTime acknowledgedAt){}
}
