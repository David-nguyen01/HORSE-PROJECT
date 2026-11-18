package com.racehorse.stable.service;

import com.racehorse.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StaffHorseGuard {
    private final JdbcTemplate jdbc;

    public StaffHorseGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void requireCaretaker(UUID horseId, UUID actor, Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> "ROLE_STABLE_STAFF".equals(a.getAuthority()))) return;
        Integer count = jdbc.queryForObject(
                "select count(*) from dbo.horses where id=? and caretaker_user_id=?",
                Integer.class, horseId, actor);
        if (count == null || count == 0) {
            throw new BusinessException("Đây không phải ngựa bạn phụ trách");
        }
    }
}
