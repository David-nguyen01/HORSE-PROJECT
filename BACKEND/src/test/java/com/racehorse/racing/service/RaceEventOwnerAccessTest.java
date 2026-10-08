package com.racehorse.racing.service;

import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.health.service.HorseMeasurementService;
import com.racehorse.horse.service.HorseAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RaceEventOwnerAccessTest {
    @Mock JdbcTemplate jdbc;
    @Mock ObjectMapper json;
    @Mock HorseMeasurementService measurement;
    @Mock HorseAccessService horseAccess;

    @Test
    void ownerListIsScopedToOwnedHorses() {
        when(jdbc.query(any(String.class), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        UUID ownerId = UUID.randomUUID();

        new RaceEventService(jdbc, json, measurement, horseAccess)
                .registrations(null, null, null, ownerId);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq(new Object[]{ownerId}));
        assertThat(sql.getValue()).contains("horse_ownerships ho", "ho.owner_id=?");
    }

    @Test
    void otherOwnerCannotDecideRegistration() {
        UUID registrationId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        RaceEventService service = spy(new RaceEventService(jdbc, json, measurement, horseAccess));
        doReturn(registration(registrationId, horseId)).when(service).registration(registrationId);
        doThrow(new ResourceNotFoundException("not owned"))
                .when(horseAccess).requireOwned(horseId, ownerId);

        assertThatThrownBy(() -> service.decide(registrationId, ownerId, true, null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(jdbc, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    void ownerCanApproveSubmittedRegistration() {
        UUID registrationId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        RaceEventService service = spy(new RaceEventService(jdbc, json, measurement, horseAccess));
        doReturn(registration(registrationId, horseId)).when(service).registration(registrationId);

        service.decide(registrationId, ownerId, true, null);

        verify(horseAccess).requireOwned(horseId, ownerId);
        verify(jdbc).update(contains("status='SUBMITTED'"), eq("APPROVE"), isNull(), any(String.class), eq(registrationId));
    }

    @Test
    void horseCannotRegisterWhileAnotherRaceIsUnfinished() {
        UUID eventId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        RaceEventService service = spy(new RaceEventService(jdbc, json, measurement, horseAccess));
        doReturn(new RaceEventService.EventView(eventId, "New race", "Track", LocalDate.now().plusDays(10),
                OffsetDateTime.now().plusDays(5), BigDecimal.valueOf(1000), "OPEN", null, null,
                null, null, true, null)).when(service).event(eventId);
        when(jdbc.queryForObject(contains("with (UPDLOCK,HOLDLOCK)"), eq(UUID.class), eq(horseId)))
                .thenReturn(horseId);
        when(jdbc.queryForObject(contains("JSON_VALUE(data_json,'$.raceEventId')=?"), eq(Integer.class),
                eq(horseId), eq(eventId.toString()))).thenReturn(0);
        when(jdbc.queryForObject(contains("e.completed_at is null"), eq(Integer.class), eq(horseId)))
                .thenReturn(1);

        assertThatThrownBy(() -> service.register(eventId, horseId, UUID.randomUUID(), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("chưa hoàn thành");
        verify(jdbc, never()).update(contains("insert into module_records"), any(Object[].class));
    }

    @Test
    void managerCanMarkPastRaceComplete() {
        UUID eventId = UUID.randomUUID();
        RaceEventService service = spy(new RaceEventService(jdbc, json, measurement, horseAccess));
        doReturn(new RaceEventService.EventView(eventId, "Past race", "Track", LocalDate.of(2025, 1, 1),
                OffsetDateTime.now().minusDays(2), BigDecimal.valueOf(1000), "OPEN", null, null,
                null, null, true, null)).when(service).event(eventId);

        service.complete(eventId);

        verify(jdbc).update(contains("completed_at=coalesce"), eq(eventId));
    }

    private RaceEventService.RegistrationView registration(UUID id, UUID horseId) {
        return new RaceEventService.RegistrationView(id, UUID.randomUUID(), "Demo race", LocalDate.now(),
                horseId, "Demo horse", UUID.randomUUID(), "SUBMITTED", BigDecimal.valueOf(400),
                OffsetDateTime.now(), null, null, null, null);
    }
}
