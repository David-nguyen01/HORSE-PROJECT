package com.racehorse.health.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.health.dto.*;
import com.racehorse.horse.service.HorseAccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.*;

@Service
public class PreventiveCareService {
    private final JdbcTemplate jdbc; private final HorseAccessService access;
    public PreventiveCareService(JdbcTemplate jdbc,HorseAccessService access){this.jdbc=jdbc;this.access=access;}
    @Transactional
    public PreventiveCareResponse create(UUID horse,UUID actor,CreatePreventiveCareRequest r){
        access.requireActive(horse); String type=r.careType().trim().toUpperCase(Locale.ROOT);
        if(!List.of("VACCINATION","DEWORMING","FARRIER").contains(type)) throw new BusinessException("careType không hợp lệ");
        UUID id=UUID.randomUUID();
        jdbc.update("insert into preventive_care_schedules(id,horse_id,care_type,details,due_date,recurrence_days,reminder_lead_days,created_by_user_id) values(?,?,?,?,?,?,?,?)",
                id,horse,type,r.details(),r.dueDate(),r.recurrenceDays(),r.reminderLeadDays(),actor);
        return new PreventiveCareResponse(id,horse,type,r.dueDate(),r.recurrenceDays(),r.reminderLeadDays(),false,null);
    }
    @Transactional
    public PreventiveCareResponse complete(UUID id,UUID actor,CompletePreventiveCareRequest r){
        // Serialize completions of the same schedule before creating its recurring successor.
        List<Map<String,Object>> rows=jdbc.queryForList(
                "select * from preventive_care_schedules with (UPDLOCK,HOLDLOCK) where id=?",id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy lịch chăm sóc: "+id);
        Map<String,Object> s=rows.getFirst();
        if(s.get("cancelled_at")!=null) throw new BusinessException("Lịch này đã bị hủy");
        if(s.get("completed_at")!=null) throw new BusinessException("Lịch này đã được hoàn thành");
        Integer recurrence=(Integer)s.get("recurrence_days"); UUID next=null;
        int changed=jdbc.update("update preventive_care_schedules set completed_by_user_id=?,completed_at=?,completion_notes=? "
                        + "where id=? and completed_at is null and cancelled_at is null",
                actor,r.performedAt(),r.notes(),id);
        if(changed!=1) throw new BusinessException("Lịch này đã được xử lý trước đó");
        if(recurrence!=null){
            next=UUID.randomUUID();
            jdbc.update("insert into preventive_care_schedules(id,horse_id,care_type,details,due_date,recurrence_days,reminder_lead_days,created_by_user_id) values(?,?,?,?,dateadd(day,?,?),?,?,?)",
                    next,s.get("horse_id"),s.get("care_type"),s.get("details"),recurrence,s.get("due_date"),recurrence,s.get("reminder_lead_days"),actor);
            jdbc.update("update preventive_care_schedules set next_schedule_id=? where id=?",next,id);
        }
        Object dueValue=s.get("due_date");
        java.time.LocalDate dueDate=dueValue instanceof java.time.LocalDate value
                ? value : ((java.sql.Date)dueValue).toLocalDate();
        return new PreventiveCareResponse(id,uuid(s.get("horse_id")),(String)s.get("care_type"),
                dueDate,recurrence,(Integer)s.get("reminder_lead_days"),true,next);
    }
    @Transactional(readOnly=true)
    public List<CareView> list(UUID horse,UUID requester,boolean privileged,String careType,String status,LocalDate from,LocalDate to){
        access.requireReadable(horse,requester,privileged);
        StringBuilder sql=new StringBuilder("select * from preventive_care_schedules where horse_id=?");
        List<Object> args=new ArrayList<>(); args.add(horse);
        if(careType!=null&&!careType.isBlank()){sql.append(" and care_type=?");args.add(careType.trim().toUpperCase(Locale.ROOT));}
        if(status!=null&&!status.isBlank()) switch(status.trim().toUpperCase(Locale.ROOT)){
            case "PENDING" -> sql.append(" and completed_at is null and cancelled_at is null");
            case "COMPLETED" -> sql.append(" and completed_at is not null");
            case "CANCELLED" -> sql.append(" and cancelled_at is not null");
            default -> throw new BusinessException("status chỉ nhận PENDING, COMPLETED hoặc CANCELLED");
        }
        if(from!=null){sql.append(" and due_date>=?");args.add(from);}
        if(to!=null){sql.append(" and due_date<=?");args.add(to);}
        sql.append(" order by due_date,id");
        return jdbc.query(sql.toString(),(rs,row)->new CareView(rs.getObject("id",UUID.class),horse,rs.getString("care_type"),
                rs.getString("details"),rs.getObject("due_date",LocalDate.class),rs.getObject("recurrence_days",Integer.class),
                rs.getInt("reminder_lead_days"),rs.getObject("completed_at",OffsetDateTime.class),rs.getString("completion_notes"),
                rs.getObject("next_schedule_id",UUID.class),rs.getObject("cancelled_at",OffsetDateTime.class)),args.toArray());
    }
    @Transactional
    public CareView update(UUID id,CreatePreventiveCareRequest r,UUID actor){
        Map<String,Object> row=requirePending(id); String type=r.careType().trim().toUpperCase(Locale.ROOT);
        if(!List.of("VACCINATION","DEWORMING","FARRIER").contains(type))throw new BusinessException("careType không hợp lệ");
        jdbc.update("update preventive_care_schedules set care_type=?,details=?,due_date=?,recurrence_days=?,reminder_lead_days=?,created_by_user_id=? where id=?",
                type,r.details(),r.dueDate(),r.recurrenceDays(),r.reminderLeadDays(),actor,id);
        UUID horse=uuid(row.get("horse_id"));
        return list(horse,actor,true,null,null,null,null).stream().filter(x->x.id().equals(id)).findFirst().orElseThrow();
    }
    @Transactional(readOnly = true)
    public List<DuePreventiveCareResponse> listDue(LocalDate asOf, String careType, boolean overdueOnly) {
        LocalDate checkDate = asOf != null ? asOf : LocalDate.now();
        StringBuilder sql = new StringBuilder("""
                select s.id, s.horse_id, h.code as horse_code, h.name as horse_name,
                       s.care_type, s.details, s.due_date, s.recurrence_days, s.reminder_lead_days
                from preventive_care_schedules s
                join horses h on h.id = s.horse_id
                where s.completed_at is null and s.cancelled_at is null
                """);
        List<Object> args = new ArrayList<>();
        if (careType != null && !careType.isBlank()) {
            sql.append(" and s.care_type = ?");
            args.add(careType.trim().toUpperCase(Locale.ROOT));
        }
        if (overdueOnly) {
            sql.append(" and s.due_date < ?");
            args.add(checkDate);
        }
        sql.append(" order by s.due_date asc, s.id asc");

        return jdbc.query(sql.toString(), (rs, rowNum) -> {
            UUID id = rs.getObject("id", UUID.class);
            UUID horseId = rs.getObject("horse_id", UUID.class);
            String horseCode = rs.getString("horse_code");
            String horseName = rs.getString("horse_name");
            String type = rs.getString("care_type");
            String details = rs.getString("details");
            LocalDate dueDate = rs.getObject("due_date", LocalDate.class);
            Integer recurrenceDays = rs.getObject("recurrence_days", Integer.class);
            int reminderLeadDays = rs.getInt("reminder_lead_days");
            boolean isOverdue = dueDate.isBefore(checkDate);
            long daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(checkDate, dueDate);

            return new DuePreventiveCareResponse(
                    id, horseId, horseCode, horseName, type, details,
                    dueDate, recurrenceDays, reminderLeadDays, isOverdue, daysUntilDue);
        }, args.toArray());
    }

    @Transactional
    public void cancel(UUID id){ requirePending(id); jdbc.update("update preventive_care_schedules set cancelled_at=SYSDATETIMEOFFSET() where id=?",id); }
    private Map<String,Object> requirePending(UUID id){
        List<Map<String,Object>> rows=jdbc.queryForList("select * from preventive_care_schedules where id=?",id);
        if(rows.isEmpty())throw new ResourceNotFoundException("Không tìm thấy lịch chăm sóc: "+id);
        Map<String,Object> row=rows.getFirst();
        if(row.get("completed_at")!=null||row.get("cancelled_at")!=null)throw new BusinessException("Chỉ có thể thay đổi lịch đang chờ");
        return row;
    }
    private UUID uuid(Object value){return value instanceof UUID id?id:UUID.fromString(String.valueOf(value));}
    public record CareView(UUID id,UUID horseId,String careType,String details,LocalDate dueDate,Integer recurrenceDays,
                           int reminderLeadDays,OffsetDateTime completedAt,String completionNotes,UUID nextScheduleId,
                           OffsetDateTime cancelledAt){}
}
