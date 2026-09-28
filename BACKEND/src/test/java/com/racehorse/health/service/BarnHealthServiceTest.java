package com.racehorse.health.service;

import tools.jackson.databind.ObjectMapper;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.health.dto.BarnHealthMapResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BarnHealthServiceTest {

    @Mock private JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private BarnHealthService service;

    @BeforeEach
    void setUp() {
        service = new BarnHealthService(jdbc, json);
    }

    @Test
    void rejectsInvalidStatusFilter() {
        assertThatThrownBy(() -> service.getBarnMap("INVALID", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("status chỉ nhận");
    }

    @Test
    void returnsBarnMapWithSummaryAndStables() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID stableId = UUID.randomUUID();
        UUID stallId = UUID.randomUUID();
        UUID lockId = UUID.randomUUID();

        // 1. Training locks query
        doAnswer(inv -> {
            RowCallbackHandler rch = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getObject("id", UUID.class)).thenReturn(lockId);
            when(rs.getObject("horse_id", UUID.class)).thenReturn(horseId);
            when(rs.getString("reason")).thenReturn("Tendon injury");
            when(rs.getObject("review_date", LocalDate.class)).thenReturn(LocalDate.of(2026, 10, 1));
            when(rs.getObject("locked_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
            rch.processRow(rs);
            return null;
        }).when(jdbc).query(contains("training_locks"), any(RowCallbackHandler.class));

        // 2. Horses query
        doAnswer(inv -> {
            RowCallbackHandler rch = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getObject("id", UUID.class)).thenReturn(horseId);
            when(rs.getString("code")).thenReturn("H-001");
            when(rs.getString("name")).thenReturn("Champion");
            when(rs.getString("gender")).thenReturn("STALLION");
            when(rs.getString("health_status")).thenReturn("INJURED");
            when(rs.getString("stable_location")).thenReturn("Barn A / Stall 1");
            rch.processRow(rs);
            return null;
        }).when(jdbc).query(contains("from horses"), any(RowCallbackHandler.class));

        // 3. Stall assignments query
        doAnswer(inv -> {
            RowCallbackHandler rch = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getObject("horse_id", UUID.class)).thenReturn(horseId);
            when(rs.getString("data_json")).thenReturn("{\"stallId\":\"" + stallId + "\"}");
            rch.processRow(rs);
            return null;
        }).when(jdbc).query(contains("STALL_ASSIGNMENT"), any(RowCallbackHandler.class));

        // 4. Stalls query
        doAnswer(inv -> {
            RowCallbackHandler rch = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getObject("id", UUID.class)).thenReturn(stallId);
            when(rs.getString("status")).thenReturn("ACTIVE");
            when(rs.getString("data_json")).thenReturn("{\"stallCode\":\"S-01\",\"stableId\":\"" + stableId + "\"}");
            rch.processRow(rs);
            return null;
        }).when(jdbc).query(contains("record_type='STALL'"), any(RowCallbackHandler.class));

        // 5. Stables query
        doAnswer(inv -> {
            RowCallbackHandler rch = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getObject("id", UUID.class)).thenReturn(stableId);
            when(rs.getString("name")).thenReturn("Barn Alpha");
            when(rs.getString("location")).thenReturn("North Sector");
            when(rs.getBoolean("is_active")).thenReturn(true);
            rch.processRow(rs);
            return null;
        }).when(jdbc).query(contains("from stables"), any(RowCallbackHandler.class));

        BarnHealthMapResponse response = service.getBarnMap(null, null);

        assertThat(response).isNotNull();
        assertThat(response.summary().totalHorses()).isEqualTo(1);
        assertThat(response.summary().injuredCount()).isEqualTo(1);
        assertThat(response.summary().lockedCount()).isEqualTo(1);
        assertThat(response.summary().fitCount()).isEqualTo(0);

        assertThat(response.stables()).hasSize(1);
        BarnHealthMapResponse.StableView stable = response.stables().getFirst();
        assertThat(stable.name()).isEqualTo("Barn Alpha");
        assertThat(stable.stalls()).hasSize(1);

        BarnHealthMapResponse.StallView stall = stable.stalls().getFirst();
        assertThat(stall.code()).isEqualTo("S-01");
        assertThat(stall.horse()).isNotNull();
        assertThat(stall.horse().healthStatus()).isEqualTo("INJURED");
        assertThat(stall.horse().trainingLock()).isNotNull();
        assertThat(stall.horse().trainingLock().isLocked()).isTrue();
        assertThat(stall.horse().trainingLock().reason()).isEqualTo("Tendon injury");
    }
}
