package com.racehorse.health.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.health.dto.*;
import com.racehorse.horse.service.HorseAccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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
        List<Map<String,Object>> rows=jdbc.queryForList("select * from preventive_care_schedules where id=? and cancelled_at is null",id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy lịch chăm sóc: "+id);
        Map<String,Object> s=rows.getFirst();
        if(s.get("completed_at")!=null) throw new BusinessException("Lịch này đã được hoàn thành");
        Integer recurrence=(Integer)s.get("recurrence_days"); UUID next=null;
        if(recurrence!=null){
            next=UUID.randomUUID();
            jdbc.update("insert into preventive_care_schedules(id,horse_id,care_type,details,due_date,recurrence_days,reminder_lead_days,created_by_user_id) values(?,?,?,?,dateadd(day,?,?),?,?,?)",
                    next,s.get("horse_id"),s.get("care_type"),s.get("details"),recurrence,s.get("due_date"),recurrence,s.get("reminder_lead_days"),actor);
        }
        jdbc.update("update preventive_care_schedules set completed_by_user_id=?,completed_at=?,completion_notes=?,next_schedule_id=? where id=? and completed_at is null",
                actor,r.performedAt(),r.notes(),next,id);
        Object dueValue=s.get("due_date");
        java.time.LocalDate dueDate=dueValue instanceof java.time.LocalDate value
                ? value : ((java.sql.Date)dueValue).toLocalDate();
        return new PreventiveCareResponse(id,uuid(s.get("horse_id")),(String)s.get("care_type"),
                dueDate,recurrence,(Integer)s.get("reminder_lead_days"),true,next);
    }
    private UUID uuid(Object value){return value instanceof UUID id?id:UUID.fromString(String.valueOf(value));}
}
