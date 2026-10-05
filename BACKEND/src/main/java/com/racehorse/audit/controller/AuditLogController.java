package com.racehorse.audit.controller;

import com.racehorse.common.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('CLUB_MANAGER')")
public class AuditLogController {
    private final JdbcTemplate jdbc;
    public AuditLogController(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @GetMapping
    public List<AuditView> list(
            @RequestParam(required=false) UUID actorId,
            @RequestParam(required=false) String module,
            @RequestParam(required=false) String action,
            @RequestParam(required=false) String entityType,
            @RequestParam(required=false) UUID entityId,
            @RequestParam(required=false) String query,
            @RequestParam(required=false) String from,
            @RequestParam(required=false) String to,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="50") int size){
        if(page<0||size<1||size>500)throw new BusinessException("Phân trang không hợp lệ");
        OffsetDateTime fromDt = parseDate(from, false);
        OffsetDateTime toDt = parseDate(to, true);
        StringBuilder sql=new StringBuilder("select a.*,u.username actor_username from audit_logs a left join users u on u.id=a.actor_user_id where 1=1");
        List<Object> args=new ArrayList<>();
        add(sql,args," and a.actor_user_id=?",actorId);
        add(sql,args," and a.module_code=?",upper(module));
        add(sql,args," and a.action_code=?",upper(action));
        add(sql,args," and a.entity_type=?",upper(entityType));
        add(sql,args," and a.entity_id=?",entityId);
        add(sql,args," and a.occurred_at>=?",fromDt);
        add(sql,args," and a.occurred_at<=?",toDt);
        if(query!=null&&!query.isBlank()){
            sql.append(" and (a.description like ? or a.action_code like ? or a.module_code like ? or u.username like ? or a.ip_address like ?)");
            String q = "%"+query.trim()+"%";
            args.add(q);args.add(q);args.add(q);args.add(q);args.add(q);
        }
        sql.append(" order by a.occurred_at desc,a.id desc offset ? rows fetch next ? rows only");
        args.add((long)page*size);args.add(size);
        return jdbc.query(sql.toString(),(rs,row)->new AuditView(rs.getLong("id"),rs.getObject("actor_user_id",UUID.class),
                rs.getString("actor_username"),rs.getObject("occurred_at",OffsetDateTime.class),rs.getString("module_code"),
                rs.getString("action_code"),rs.getString("entity_type"),rs.getObject("entity_id",UUID.class),
                rs.getString("description"),rs.getString("old_values_json"),rs.getString("new_values_json"),
                rs.getString("ip_address"),rs.getString("user_agent")),args.toArray());
    }

    @GetMapping("/count")
    public Map<String, Object> count(
            @RequestParam(required=false) UUID actorId,
            @RequestParam(required=false) String module,
            @RequestParam(required=false) String action,
            @RequestParam(required=false) String entityType,
            @RequestParam(required=false) String query,
            @RequestParam(required=false) String from,
            @RequestParam(required=false) String to) {
        OffsetDateTime fromDt = parseDate(from, false);
        OffsetDateTime toDt = parseDate(to, true);
        StringBuilder sql = new StringBuilder("select count(*) from audit_logs a left join users u on u.id=a.actor_user_id where 1=1");
        List<Object> args = new ArrayList<>();
        add(sql, args, " and a.actor_user_id=?", actorId);
        add(sql, args, " and a.module_code=?", upper(module));
        add(sql, args, " and a.action_code=?", upper(action));
        add(sql, args, " and a.entity_type=?", upper(entityType));
        add(sql, args, " and a.occurred_at>=?", fromDt);
        add(sql, args, " and a.occurred_at<=?", toDt);
        if(query!=null&&!query.isBlank()){
            sql.append(" and (a.description like ? or a.action_code like ? or a.module_code like ? or u.username like ? or a.ip_address like ?)");
            String q = "%"+query.trim()+"%";
            args.add(q);args.add(q);args.add(q);args.add(q);args.add(q);
        }
        Integer count = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        return Map.of("total", count != null ? count : 0);
    }

    @GetMapping(value = "/export", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> export(
            @RequestParam(required=false) UUID actorId,
            @RequestParam(required=false) String module,
            @RequestParam(required=false) String action,
            @RequestParam(required=false) String entityType,
            @RequestParam(required=false) String query,
            @RequestParam(required=false) String from,
            @RequestParam(required=false) String to) {
        OffsetDateTime fromDt = parseDate(from, false);
        OffsetDateTime toDt = parseDate(to, true);
        StringBuilder sql = new StringBuilder("select a.*, u.username actor_username from audit_logs a left join users u on u.id=a.actor_user_id where 1=1");
        List<Object> args = new ArrayList<>();
        add(sql, args, " and a.actor_user_id=?", actorId);
        add(sql, args, " and a.module_code=?", upper(module));
        add(sql, args, " and a.action_code=?", upper(action));
        add(sql, args, " and a.entity_type=?", upper(entityType));
        add(sql, args, " and a.occurred_at>=?", fromDt);
        add(sql, args, " and a.occurred_at<=?", toDt);
        if(query!=null&&!query.isBlank()){
            sql.append(" and (a.description like ? or a.action_code like ? or a.module_code like ? or u.username like ? or a.ip_address like ?)");
            String q = "%"+query.trim()+"%";
            args.add(q);args.add(q);args.add(q);args.add(q);args.add(q);
        }
        sql.append(" order by a.occurred_at desc, a.id desc offset 0 rows fetch next 5000 rows only");

        List<AuditView> logs = jdbc.query(sql.toString(), (rs, row) -> new AuditView(
                rs.getLong("id"), rs.getObject("actor_user_id", UUID.class),
                rs.getString("actor_username"), rs.getObject("occurred_at", OffsetDateTime.class),
                rs.getString("module_code"), rs.getString("action_code"), rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class), rs.getString("description"),
                rs.getString("old_values_json"), rs.getString("new_values_json"),
                rs.getString("ip_address"), rs.getString("user_agent")), args.toArray());

        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF");
        csv.append("ID,Thời điểm,Người thao tác,Phân hệ,Hành động,Đối tượng,Mã đối tượng,Mô tả,Địa chỉ IP,Thiết bị\n");
        for (AuditView v : logs) {
            csv.append(v.id()).append(",")
               .append(csvEscape(v.occurredAt() != null ? v.occurredAt().toString() : "")).append(",")
               .append(csvEscape(v.actorUsername() != null ? v.actorUsername() : "Hệ thống")).append(",")
               .append(csvEscape(v.module())).append(",")
               .append(csvEscape(v.action())).append(",")
               .append(csvEscape(v.entityType() != null ? v.entityType() : "")).append(",")
               .append(csvEscape(v.entityId() != null ? v.entityId().toString() : "")).append(",")
               .append(csvEscape(v.description())).append(",")
               .append(csvEscape(v.ipAddress() != null ? v.ipAddress() : "")).append(",")
               .append(csvEscape(v.userAgent() != null ? v.userAgent() : "")).append("\n");
        }

        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=nhat-ky-kiem-toan.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping("/{id}")
    public AuditView get(@PathVariable long id){
        return jdbc.query("select a.*,u.username actor_username from audit_logs a left join users u on u.id=a.actor_user_id where a.id=?",rs->{if(!rs.next())throw new com.racehorse.common.exception.ResourceNotFoundException("Không tìm thấy audit log: "+id);return new AuditView(rs.getLong("id"),rs.getObject("actor_user_id",UUID.class),rs.getString("actor_username"),rs.getObject("occurred_at",OffsetDateTime.class),rs.getString("module_code"),rs.getString("action_code"),rs.getString("entity_type"),rs.getObject("entity_id",UUID.class),rs.getString("description"),rs.getString("old_values_json"),rs.getString("new_values_json"),rs.getString("ip_address"),rs.getString("user_agent"));},id);
    }

    private OffsetDateTime parseDate(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) return null;
        value = value.trim();
        try {
            if (value.length() == 10) {
                LocalDate d = LocalDate.parse(value);
                return endOfDay ? d.atTime(23, 59, 59, 999000000).atOffset(ZoneOffset.UTC)
                                : d.atStartOfDay().atOffset(ZoneOffset.UTC);
            }
            return OffsetDateTime.parse(value);
        } catch (Exception e) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(value);
                return ldt.atOffset(ZoneOffset.UTC);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private String csvEscape(String text) {
        if (text == null) return "\"\"";
        return "\"" + text.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ") + "\"";
    }

    private void add(StringBuilder sql,List<Object> args,String clause,Object value){if(value!=null){sql.append(clause);args.add(value);}}
    private String upper(String value){return value==null||value.isBlank()?null:value.trim().toUpperCase(Locale.ROOT);}
    public record AuditView(long id,UUID actorId,String actorUsername,OffsetDateTime occurredAt,String module,
                            String action,String entityType,UUID entityId,String description,String oldValuesJson,
                            String newValuesJson,String ipAddress,String userAgent){}
}
