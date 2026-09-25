package com.racehorse.admin;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.auth.entity.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.racehorse.audit.service.AuditService;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class AdminManagementService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final ObjectMapper json;
    public AdminManagementService(JdbcTemplate jdbc, PasswordEncoder encoder,AuditService audit,ObjectMapper json) {this.jdbc=jdbc;this.encoder=encoder;this.audit=audit;this.json=json;}

    @Transactional(readOnly=true)
    public List<Map<String,Object>> accounts(String query, Role role, Boolean enabled, int page, int size) {
        pageCheck(page,size);
        StringBuilder sql=new StringBuilder("select u.id,u.username,u.email,u.enabled from users u where 1=1");
        List<Object> args=new ArrayList<>();
        if(query!=null&&!query.isBlank()){sql.append(" and (u.username like ? or u.email like ?)");args.add("%"+query.trim()+"%");args.add("%"+query.trim()+"%");}
        if(role!=null){sql.append(" and exists(select 1 from user_roles r where r.user_id=u.id and r.role=?)");args.add(role.name());}
        if(enabled!=null){sql.append(" and u.enabled=?");args.add(enabled);}
        sql.append(" order by u.username,u.id offset ? rows fetch next ? rows only");args.add((long)page*size);args.add(size);
        return jdbc.query(sql.toString(),(rs,n)->{
            UUID id=rs.getObject("id",UUID.class);
            Map<String,Object> row=new LinkedHashMap<>();row.put("id",id);row.put("username",rs.getString("username"));row.put("email",rs.getString("email"));row.put("enabled",rs.getBoolean("enabled"));
            row.put("roles",jdbc.queryForList("select role from user_roles where user_id=? order by role",String.class,id));return row;
        },args.toArray());
    }

    @Transactional
    public void updateAccount(UUID actor,UUID id,String username,String email){
        Map<String,Object> before=userSnapshot(id); username=username.trim().toLowerCase(Locale.ROOT);email=email.trim().toLowerCase(Locale.ROOT);
        if(username.length()<3)throw new BusinessException("Tên tài khoản cần ít nhất 3 ký tự");
        Integer duplicate=jdbc.queryForObject("select count(*) from users where id<>? and (username=? or email=?)",Integer.class,id,username,email);
        if(duplicate!=null&&duplicate>0)throw new BusinessException("Tên tài khoản hoặc email đã tồn tại");
        jdbc.update("update users set username=?,email=?,updated_at=SYSDATETIMEOFFSET(),version=version+1 where id=?",username,email,id);
        writeAudit(actor,"UPDATE",id,"Cập nhật thông tin tài khoản",before,userSnapshot(id));
    }

    @Transactional
    public void setEnabled(UUID actor,UUID id,boolean enabled){
        Map<String,Object> before=userSnapshot(id);
        boolean currentEnabled=Boolean.TRUE.equals(before.get("enabled"));
        if(currentEnabled==enabled)return;
        if(!enabled&&actor.equals(id))throw new BusinessException("Không thể tự khóa tài khoản đang sử dụng");
        // Serialize manager lifecycle changes so two managers cannot disable each other concurrently.
        List<UUID> managers=jdbc.query("select u.id from users u with (updlock,holdlock) where u.enabled=1 and exists(select 1 from user_roles r where r.user_id=u.id and r.role='CLUB_MANAGER')",(rs,n)->rs.getObject(1,UUID.class));
        if(!enabled&&managers.contains(id)&&managers.size()<=1)throw new BusinessException("Phải giữ ít nhất một Club Manager hoạt động");
        jdbc.update("update users set enabled=?,updated_at=SYSDATETIMEOFFSET(),version=version+1 where id=?",enabled,id);
        if(!enabled)revokeSessions(id);
        writeAudit(actor,"STATUS_CHANGE",id,enabled?"Mở khóa tài khoản":"Khóa tài khoản",
                Map.of("enabled",currentEnabled),Map.of("enabled",enabled));
    }

    @Transactional
    public void resetPassword(UUID actor,UUID id,String password){requireUser(id);jdbc.update("update users set password_hash=?,updated_at=SYSDATETIMEOFFSET(),version=version+1 where id=?",encoder.encode(password),id);revokeSessions(id);writeAudit(actor,"PASSWORD_RESET",id,"Đặt lại mật khẩu và thu hồi phiên",null,Map.of("sessionsRevoked",true));}
    private void revokeSessions(UUID id){jdbc.update("update login_sessions set revoked_at=SYSDATETIMEOFFSET(),updated_at=SYSDATETIMEOFFSET(),version=version+1 where user_id=? and revoked_at is null",id);}
    private void requireUser(UUID id){Integer n=jdbc.queryForObject("select count(*) from users where id=?",Integer.class,id);if(n==null||n==0)throw new ResourceNotFoundException("Không tìm thấy tài khoản");}

    @Transactional(readOnly=true)
    public List<Map<String,Object>> archivedHorses(String query,int page,int size){
        pageCheck(page,size);String pattern="%"+(query==null?"":query.trim())+"%";
        return jdbc.query("select id,code,name,breed,status,archived_at from horses where archived_at is not null and (name like ? or code like ?) order by archived_at desc,id offset ? rows fetch next ? rows only",(rs,n)->{
            Map<String,Object> r=new LinkedHashMap<>();r.put("id",rs.getObject("id",UUID.class));r.put("code",rs.getString("code"));r.put("name",rs.getString("name"));r.put("breed",rs.getString("breed"));r.put("status",rs.getString("status"));r.put("archivedAt",rs.getObject("archived_at",java.time.OffsetDateTime.class));return r;
        },pattern,pattern,(long)page*size,size);
    }
    @Transactional
    public void restore(UUID actor,UUID id){
        int n=jdbc.update("update horses set archived_at=null,updated_at=SYSDATETIMEOFFSET(),version=version+1 where id=? and archived_at is not null",id);
        if(n==0)throw new BusinessException("Không tìm thấy hồ sơ đang lưu trữ để khôi phục");
        audit.record(actor,"PROFILE","RESTORE","HORSE",id,"Khôi phục hồ sơ ngựa",null,toJson(Map.of("archived",false)),null,null);
    }

    @Transactional
    public void updateServiceAccess(UUID actor, UUID horseId, String paymentStatus, Boolean serviceOverride, String overrideReason, java.time.LocalDate paidUntil) {
        Integer exists = jdbc.queryForObject("select count(*) from horses where id=?", Integer.class, horseId);
        if (exists == null || exists == 0) throw new ResourceNotFoundException("Không tìm thấy ngựa");

        Map<String, Object> before = jdbc.queryForMap(
                "select name, payment_status, service_override, override_reason, paid_until from horses where id=?", horseId);

        String currentStatus = (String) before.get("payment_status");
        String newStatus = (paymentStatus != null && !paymentStatus.isBlank())
                ? paymentStatus.trim().toUpperCase(Locale.ROOT)
                : (currentStatus != null ? currentStatus : "UNPAID");
        if (!"PAID".equals(newStatus) && !"UNPAID".equals(newStatus)) {
            throw new BusinessException("Trạng thái thanh toán không hợp lệ (chỉ chấp nhận PAID hoặc UNPAID)");
        }

        boolean currentOverride = Boolean.TRUE.equals(before.get("service_override"));
        boolean newOverride = serviceOverride != null ? serviceOverride : currentOverride;

        String newReason = overrideReason != null ? overrideReason.trim() : (String) before.get("override_reason");
        if (!newOverride) {
            newReason = null;
        } else if (newReason == null || newReason.isBlank()) {
            newReason = "Quản lý CLB bảo lãnh dịch vụ";
        }

        java.time.LocalDate newPaidUntil = paidUntil;
        if (newPaidUntil == null && before.get("paid_until") != null) {
            newPaidUntil = ((java.sql.Date) before.get("paid_until")).toLocalDate();
        }

        jdbc.update(
                "update horses set payment_status=?, service_override=?, override_reason=?, paid_until=?, updated_at=SYSDATETIMEOFFSET(), version=version+1 where id=?",
                newStatus, newOverride, newReason, newPaidUntil, horseId);

        Map<String, Object> after = Map.of(
                "paymentStatus", newStatus,
                "serviceOverride", newOverride,
                "overrideReason", newReason != null ? newReason : "",
                "paidUntil", newPaidUntil != null ? newPaidUntil.toString() : ""
        );

        String auditDesc = "Cập nhật trạng thái thanh toán và quyền dịch vụ của ngựa";
        if (currentOverride && !newOverride) {
            auditDesc = "Thu hồi bảo lãnh dịch vụ của ngựa";
        } else if (!currentOverride && newOverride) {
            auditDesc = "Club Manager duyệt bảo lãnh dịch vụ của ngựa: " + newReason;
        }

        audit.record(actor, "PROFILE", "SERVICE_ACCESS_OVERRIDE", "HORSE", horseId,
                auditDesc, toJson(before), toJson(after), null, null);
    }

    private Map<String,Object> userSnapshot(UUID id){requireUser(id);return jdbc.queryForMap("select username,email,enabled from users where id=?",id);}
    private void writeAudit(UUID actor,String action,UUID id,String description,Object before,Object after){audit.record(actor,"ACCESS",action,"USER",id,description,toJson(before),toJson(after),null,null);}
    private String toJson(Object value){if(value==null)return null;try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Không thể ghi dữ liệu audit",e);}}
    private void pageCheck(int page,int size){if(page<0||size<1||size>100)throw new BusinessException("Phân trang không hợp lệ");}
}
