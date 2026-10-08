package com.racehorse.health.service;

import com.racehorse.health.dto.CreateHorseWeightRequest;
import com.racehorse.horse.service.HorseAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HorseMeasurementServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock HorseAccessService access;
    @InjectMocks HorseMeasurementService service;

    @Test
    void veterinarianWeightReplacesCurrentHorseWeightWithoutCreatingTimelineRow() {
        UUID horseId = UUID.randomUUID();
        UUID veterinarianId = UUID.randomUUID();
        OffsetDateTime measuredAt = OffsetDateTime.now();
        CreateHorseWeightRequest request = new CreateHorseWeightRequest(
                new BigDecimal("487.50"), measuredAt, "Cân sau kiểm tra");

        var saved = service.addWeight(horseId, veterinarianId, request);

        assertThat(saved.horseId()).isEqualTo(horseId);
        assertThat(saved.weightKg()).isEqualByComparingTo("487.50");
        verify(jdbc).update(contains("update horses set weight_kg="),
                eq(request.weightKg()), eq(saved.id()), eq(measuredAt),
                eq(veterinarianId), eq(request.notes()), eq(horseId));
    }

    @Test
    void weightHistoryEndpointReturnsOnlyCurrentHorseWeight() throws Exception {
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        OffsetDateTime measuredAt = OffsetDateTime.now();
        when(jdbc.query(contains("from horses where id=? and weight_kg is not null"),
                any(RowMapper.class), eq(horseId))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<com.racehorse.health.dto.HorseWeightResponse> mapper = invocation.getArgument(1);
            ResultSet row = mock(ResultSet.class);
            when(row.getObject(1, UUID.class)).thenReturn(recordId);
            when(row.getObject(2, UUID.class)).thenReturn(horseId);
            when(row.getObject(3, UUID.class)).thenReturn(userId);
            when(row.getBigDecimal(4)).thenReturn(new BigDecimal("487.50"));
            when(row.getObject(5, OffsetDateTime.class)).thenReturn(measuredAt);
            return List.of(mapper.mapRow(row, 0));
        });

        var weights = service.weights(horseId, userId, false);

        assertThat(weights).hasSize(1);
        assertThat(weights.getFirst().id()).isEqualTo(recordId);
        assertThat(weights.getFirst().weightKg()).isEqualByComparingTo("487.50");
    }
}
