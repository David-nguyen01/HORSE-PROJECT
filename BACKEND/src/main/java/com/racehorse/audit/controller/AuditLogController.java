package com.racehorse.audit.controller;

import com.racehorse.common.exception.BusinessException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('CLUB_MANAGER')")
public class AuditLogController {
    private final JdbcTemplate jdbc;
    public AuditLogController(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @GetMapping
    List<AuditView> list(@RequestParam(required=false) UUID actorId,@RequestParam(required=false) String module,
            @RequestParam(required=false) String action,@RequestParam(required=false) String entityType,
            @RequestParam(required=false) UUID entityId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="100") int size){
        if(page<0||size<1||size>500)throw new BusinessException("Phân trang không hợp lệ");
        StringBuilder sql=new StringBuilder("select a.*,u.username actor_username from audit_logs a left join users u on u.id=a.actor_user_id where 1=1");
        List<Object> args=new ArrayList<>();
        add(sql,args," and a.actor_user_id=?",actorId); add(sql,args," and a.module_code=?",upper(module));
        add(sql,args," and a.action_code=?",upper(action)); add(sql,args," and a.entity_type=?",upper(entityType));
        add(sql,args," and a.entity_id=?",entityId); add(sql,args," and a.occurred_at>=?",from); add(sql,args," and a.occurred_at<=?",to);
        sql.append(" order by a.occurred_at desc,a.id desc offset ? rows fetch next ? rows only");args.add(page*size);args.add(size);
        return jdbc.query(sql.toString(),(rs,row)->new AuditView(rs.getLong("id"),rs.getObject("actor_user_id",UUID.class),
                rs.getString("actor_username"),rs.getObject("occurred_at",OffsetDateTime.class),rs.getString("module_code"),
                rs.getString("action_code"),rs.getString("entity_type"),rs.getObject("entity_id",UUID.class),
                rs.getString("description"),rs.getString("old_values_json"),rs.getString("new_values_json"),
                rs.getString("ip_address"),rs.getString("user_agent")),args.toArray());
    }
    @GetMapping("/{id}")
    AuditView get(@PathVariable long id){
        return jdbc.query("select a.*,u.username actor_username from audit_logs a left join users u on u.id=a.actor_user_id where a.id=?",rs->{if(!rs.next())throw new com.racehorse.common.exception.ResourceNotFoundException("Không tìm thấy audit log: "+id);return new AuditView(rs.getLong("id"),rs.getObject("actor_user_id",UUID.class),rs.getString("actor_username"),rs.getObject("occurred_at",OffsetDateTime.class),rs.getString("module_code"),rs.getString("action_code"),rs.getString("entity_type"),rs.getObject("entity_id",UUID.class),rs.getString("description"),rs.getString("old_values_json"),rs.getString("new_values_json"),rs.getString("ip_address"),rs.getString("user_agent"));},id);
    }
    private void add(StringBuilder sql,List<Object> args,String clause,Object value){if(value!=null){sql.append(clause);args.add(value);}}
    private String upper(String value){return value==null||value.isBlank()?null:value.trim().toUpperCase(Locale.ROOT);}
    public record AuditView(long id,UUID actorId,String actorUsername,OffsetDateTime occurredAt,String module,
                            String action,String entityType,UUID entityId,String description,String oldValuesJson,
                            String newValuesJson,String ipAddress,String userAgent){}
}
