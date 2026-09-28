package com.racehorse.automation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Component
public class DailyAutomationJob {
    private final JdbcTemplate jdbc; private final ZoneId zone;
    public DailyAutomationJob(JdbcTemplate jdbc,@Value("${app.club-time-zone}") String zone){this.jdbc=jdbc;this.zone=ZoneId.of(zone);}

    @Scheduled(cron="${app.jobs.daily-cron}",zone="${app.club-time-zone}")
    @Transactional
    public void runAtSixEveryDay(){ LocalDate today=LocalDate.now(zone); remindPreventiveCare(today); createOwnerReports(today.minusDays(1)); }

    void remindPreventiveCare(LocalDate today){
        jdbc.update("""
                insert into notifications(id,recipient_user_id,notification_type,priority,title,message,related_entity_type,related_entity_id,deduplication_key)
                select newid(),ur.user_id,'PREVENTIVE_CARE_DUE',
                       case when s.due_date<? then 'HIGH' else 'NORMAL' end,
                       N'Nhắc lịch chăm sóc phòng ngừa',concat(N'Lịch ',s.care_type,N' đến hạn ',convert(varchar(10),s.due_date,23)),
                       'PREVENTIVE_CARE',s.id,concat('preventive:',cast(s.id as varchar(36)),':',convert(varchar(10),s.due_date,23),':',cast(ur.user_id as varchar(36)))
                from preventive_care_schedules s cross join user_roles ur
                join users u on u.id = ur.user_id
                where ur.role in ('VETERINARIAN', 'HEAD_TRAINER') and u.enabled = 1
                  and s.cancelled_at is null
                  and dateadd(day,-s.reminder_lead_days,s.due_date)<=?
                  and s.completed_at is null
                  and not exists(select 1 from notifications n where n.deduplication_key=concat('preventive:',cast(s.id as varchar(36)),':',convert(varchar(10),s.due_date,23),':',cast(ur.user_id as varchar(36))))
                """,today,today);
    }

    void createOwnerReports(LocalDate day){
        UUID generator=jdbc.query("select top 1 u.id from users u join user_roles ur on ur.user_id=u.id where ur.role='CLUB_MANAGER' and u.enabled=1 order by u.created_at",
                rs->rs.next()?rs.getObject(1,UUID.class):null);
        if(generator==null) return;
        List<UUID> owners=jdbc.query("select distinct owner_id from horse_ownerships",(rs,row)->rs.getObject(1,UUID.class));
        for(UUID owner:owners){
            Integer exists=jdbc.queryForObject("select count(*) from owner_reports where owner_user_id=? and period_start=? and period_end=?",Integer.class,owner,day,day);
            if(exists!=null&&exists>0) continue;
            UUID report=UUID.randomUUID();
            jdbc.update("""
                    insert into owner_reports(id,owner_user_id,period_start,period_end,generated_at,generated_by_user_id,horse_ids_json,transaction_ids_json)
                    select ?,?,?,?,?,?,
                        (select ho.horse_id as id from horse_ownerships ho where ho.owner_id=? for json path),
                        (select ft.id from financial_transactions ft join horse_ownerships ho on ho.horse_id=ft.horse_id
                         where ho.owner_id=? and ft.transaction_date=? for json path)
                    """,report,owner,day,day,OffsetDateTime.now(zone),generator,owner,owner,day);
            jdbc.update("insert into notifications(id,recipient_user_id,notification_type,priority,title,message,related_entity_type,related_entity_id,deduplication_key) values(newid(),?,'OWNER_REPORT_READY','NORMAL',N'Báo cáo ngày mới',N'Báo cáo ngày '+convert(varchar(10),?,23)+N' đã sẵn sàng (VND).','OWNER_REPORT',?,?)",
                    owner,day,report,"owner-report:"+owner+":"+day);
        }
    }
}
