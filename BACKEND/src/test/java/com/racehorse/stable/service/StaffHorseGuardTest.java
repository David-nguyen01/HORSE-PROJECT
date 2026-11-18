package com.racehorse.stable.service;

import com.racehorse.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class StaffHorseGuardTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final StaffHorseGuard guard = new StaffHorseGuard(jdbc);
    private final UUID horseId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @Test
    void staffCannotOperateAnotherCaretakersHorse() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(horseId), eq(actorId))).thenReturn(0);
        assertThrows(BusinessException.class, () -> guard.requireCaretaker(horseId, actorId, staff()));
    }

    @Test
    void assignedStaffCanOperateHorse() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(horseId), eq(actorId))).thenReturn(1);
        assertDoesNotThrow(() -> guard.requireCaretaker(horseId, actorId, staff()));
    }

    @Test
    void managerDoesNotNeedCaretakerAssignment() {
        Authentication manager = new UsernamePasswordAuthenticationToken("manager", "", List.of(
                new SimpleGrantedAuthority("ROLE_CLUB_MANAGER")));
        assertDoesNotThrow(() -> guard.requireCaretaker(horseId, actorId, manager));
        verifyNoInteractions(jdbc);
    }

    private Authentication staff() {
        return new UsernamePasswordAuthenticationToken("staff", "", List.of(
                new SimpleGrantedAuthority("ROLE_STABLE_STAFF")));
    }
}
