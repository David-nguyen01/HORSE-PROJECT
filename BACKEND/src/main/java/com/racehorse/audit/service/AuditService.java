package com.racehorse.audit.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditService {

    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Async
    public void record(UUID actor, String module, String action, String entityType, UUID entityId,
                       String description, String ipAddress, String userAgent) {
        try {
            jdbc.update("""
                    INSERT INTO audit_logs (actor_user_id, module_code, action_code, entity_type, entity_id, description, ip_address, user_agent)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, actor, module, action, entityType, entityId, description, ipAddress, userAgent);
        } catch (Exception e) {
            // Log it but don't fail the request since this is an audit logger
        }
    }
}
