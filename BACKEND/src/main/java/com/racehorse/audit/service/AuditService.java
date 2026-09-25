package com.racehorse.audit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String module, String action, String entityType, UUID entityId,
                       String description, String ipAddress, String userAgent) {
        record(actorId,module,action,entityType,entityId,description,null,null,ipAddress,userAgent);
    }

    @Transactional
    public void record(UUID actorId,String module,String action,String entityType,UUID entityId,String description,
                       String oldValuesJson,String newValuesJson,String ipAddress,String userAgent) {
        try {
            if ((ipAddress == null || userAgent == null)
                    && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                if (ipAddress == null) ipAddress = attributes.getRequest().getRemoteAddr();
                if (userAgent == null) userAgent = attributes.getRequest().getHeader("User-Agent");
            }
            jdbc.update("""
                    insert into audit_logs(actor_user_id,occurred_at,module_code,action_code,entity_type,entity_id,
                                           description,old_values_json,new_values_json,ip_address,user_agent)
                    values(?,?,?,?,?,?,?,?,?,?,?)
                    """, actorId, OffsetDateTime.now(), module, action, entityType, entityId,
                    description,oldValuesJson,newValuesJson,trim(ipAddress, 45), trim(userAgent, 500));
        } catch (RuntimeException exception) {
            log.error("Không thể ghi audit log cho {} {}", action, entityType, exception);
        }
    }

    private String trim(String value, int max) {
        return value == null ? null : value.substring(0, Math.min(value.length(), max));
    }
}
