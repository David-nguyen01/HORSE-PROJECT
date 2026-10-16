package com.racehorse.training.service;

import com.racehorse.common.exception.InvalidInputException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.training.dto.CreateMetricThresholdRequest;
import com.racehorse.training.dto.CreateTelemetryRequest;
import com.racehorse.training.dto.TelemetryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelemetryServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private HorseAccessService access;

    private TelemetryService service;

    private final UUID horseId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TelemetryService(jdbc, access);
    }

    @Test
    void configureThresholdValidatesMetricType() {
        CreateMetricThresholdRequest req = new CreateMetricThresholdRequest(
                "TEMPERATURE", BigDecimal.TEN, BigDecimal.valueOf(40), "C", null, null);

        assertThatThrownBy(() -> service.configureThreshold(horseId, actorId, true, false, req))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("metricType chỉ nhận HEART_RATE hoặc SPEED");
    }

    @Test
    void configureThresholdValidatesMinAndMax() {
        CreateMetricThresholdRequest bothNull = new CreateMetricThresholdRequest(
                "HEART_RATE", null, null, "bpm", null, null);
        assertThatThrownBy(() -> service.configureThreshold(horseId, actorId, true, false, bothNull))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Phải nhập ít nhất một ngưỡng");

        CreateMetricThresholdRequest minGeMax = new CreateMetricThresholdRequest(
                "HEART_RATE", BigDecimal.valueOf(100), BigDecimal.valueOf(80), "bpm", null, null);
        assertThatThrownBy(() -> service.configureThreshold(horseId, actorId, true, false, minGeMax))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Ngưỡng tối thiểu phải nhỏ hơn tối đa");
    }

    @Test
    void configureThresholdSavesValidThreshold() {
        CreateMetricThresholdRequest req = new CreateMetricThresholdRequest(
                "HEART_RATE", BigDecimal.valueOf(40), BigDecimal.valueOf(220), "bpm",
                OffsetDateTime.now(), OffsetDateTime.now().plusMonths(6));

        when(jdbc.update(anyString(), any(), eq(horseId), eq("HEART_RATE"), any(), any(), eq("bpm"), eq(actorId), any(), any()))
                .thenReturn(1);

        UUID thresholdId = service.configureThreshold(horseId, actorId, true, false, req);

        assertThat(thresholdId).isNotNull();
        verify(access).requireActive(horseId);
        verify(jdbc).update(contains("insert into horse_metric_thresholds"), any(), eq(horseId), eq("HEART_RATE"),
                eq(BigDecimal.valueOf(40)), eq(BigDecimal.valueOf(220)), eq("bpm"), eq(actorId), any(), any());
    }

    @Test
    void updateThresholdThrowsWhenNotFound() {
        UUID thresholdId = UUID.randomUUID();
        CreateMetricThresholdRequest req = new CreateMetricThresholdRequest(
                "SPEED", BigDecimal.ZERO, BigDecimal.valueOf(65), "km/h", null, null);

        when(jdbc.queryForObject(contains("select count(*) from horse_metric_thresholds"), eq(Integer.class), eq(thresholdId), eq(horseId)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.updateThreshold(horseId, thresholdId, actorId, true, false, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy ngưỡng");
    }

    @Test
    void endThresholdThrowsWhenNotFound() {
        UUID thresholdId = UUID.randomUUID();
        when(jdbc.update(contains("update horse_metric_thresholds set effective_to="), eq(thresholdId), eq(horseId)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.endThreshold(horseId, thresholdId, actorId, true, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy ngưỡng");
    }

    @Test
    void enterTelemetryValidatesContextAndReadings() {
        OffsetDateTime now = OffsetDateTime.now();
        CreateTelemetryRequest invalidContext = new CreateTelemetryRequest(
                null, "FLYING", now, now.plusMinutes(30), List.of());

        assertThatThrownBy(() -> service.enter(horseId, actorId, true, false, invalidContext))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("measurementContext không hợp lệ");

        CreateTelemetryRequest invalidTimes = new CreateTelemetryRequest(
                null, "TRAINING", now, now.minusMinutes(10), List.of());

        assertThatThrownBy(() -> service.enter(horseId, actorId, true, false, invalidTimes))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("endedAt phải sau startedAt");

        CreateTelemetryRequest.Reading emptyReading = new CreateTelemetryRequest.Reading(now, null, null);
        CreateTelemetryRequest req = new CreateTelemetryRequest(
                null, "TRAINING", now, now.plusMinutes(15), List.of(emptyReading));

        assertThatThrownBy(() -> service.enter(horseId, actorId, true, false, req))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Mỗi dòng phải có nhịp tim hoặc tốc độ");
    }

    @Test
    void enterTelemetryRecordsReadingsAndEvaluatesAlerts() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID thresholdId = UUID.randomUUID();

        CreateTelemetryRequest.Reading reading = new CreateTelemetryRequest.Reading(
                now, 230, new BigDecimal("65.5"));
        CreateTelemetryRequest req = new CreateTelemetryRequest(
                null, "TRAINING", now, now.plusMinutes(30), List.of(reading));

        // Mock insertReading key generation
        doAnswer(inv -> {
            KeyHolder kh = inv.getArgument(1);
            Map<String, Object> keys = Map.of("id", 101L);
            kh.getKeyList().add(keys);
            return 1;
        }).when(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        // Mock evaluate HEART_RATE: max is 220, reading is 230 -> breach MAXIMUM
        when(jdbc.queryForList(contains("from horse_metric_thresholds"), eq(horseId), eq("HEART_RATE"), any(), any()))
                .thenReturn(List.of(Map.of("id", thresholdId, "minimum_value", BigDecimal.valueOf(40), "maximum_value", BigDecimal.valueOf(220))));

        // Mock evaluate SPEED: max is 70, reading is 65.5 -> no breach
        when(jdbc.queryForList(contains("from horse_metric_thresholds"), eq(horseId), eq("SPEED"), any(), any()))
                .thenReturn(List.of(Map.of("id", thresholdId, "minimum_value", BigDecimal.valueOf(5), "maximum_value", BigDecimal.valueOf(70))));

        TelemetryResponse resp = service.enter(horseId, actorId, true, false, req);

        assertThat(resp).isNotNull();
        assertThat(resp.horseId()).isEqualTo(horseId);
        assertThat(resp.readingCount()).isEqualTo(1);
        assertThat(resp.alerts()).hasSize(1);
        assertThat(resp.alerts().get(0).metricType()).isEqualTo("HEART_RATE");
        assertThat(resp.alerts().get(0).direction()).isEqualTo("MAXIMUM");

        verify(jdbc).update(contains("insert into alert_events"), any(), eq(horseId), any(), eq(101L),
                eq(thresholdId), eq("HEART_RATE"), eq(BigDecimal.valueOf(230)), eq(BigDecimal.valueOf(220)), eq("MAXIMUM"), any());
        verify(jdbc).update(contains("insert into notifications"), anyString(), any(), any());
    }

    @Test
    void acknowledgeThrowsWhenAlertNotFound() {
        UUID alertId = UUID.randomUUID();
        when(jdbc.update(contains("update alert_events set acknowledged_by_user_id="), eq(actorId), eq(alertId), eq(horseId)))
                .thenReturn(0);
        when(jdbc.queryForObject(contains("select count(*) from alert_events"), eq(Integer.class), eq(alertId), eq(horseId)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.acknowledge(horseId, alertId, actorId, true, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy cảnh báo");
    }

    @Test
    void configureThresholdValidatesEffectiveDates() {
        OffsetDateTime from = OffsetDateTime.now();
        OffsetDateTime to = from.minusDays(1);
        CreateMetricThresholdRequest req = new CreateMetricThresholdRequest(
                "HEART_RATE", BigDecimal.valueOf(40), BigDecimal.valueOf(220), "bpm", from, to);

        assertThatThrownBy(() -> service.configureThreshold(horseId, actorId, true, false, req))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("effectiveTo phải sau effectiveFrom");
    }

    @Test
    void updateThresholdValidatesEffectiveDates() {
        UUID thresholdId = UUID.randomUUID();
        when(jdbc.queryForObject(contains("select count(*) from horse_metric_thresholds"), eq(Integer.class), eq(thresholdId), eq(horseId)))
                .thenReturn(1);

        OffsetDateTime from = OffsetDateTime.now();
        OffsetDateTime to = from.minusHours(2);
        CreateMetricThresholdRequest req = new CreateMetricThresholdRequest(
                "SPEED", BigDecimal.ZERO, BigDecimal.valueOf(60), "km/h", from, to);

        assertThatThrownBy(() -> service.updateThreshold(horseId, thresholdId, actorId, true, false, req))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("effectiveTo phải sau effectiveFrom");
    }

    @Test
    void acknowledgeUpdatesAndReturnsDirectAlertView() {
        UUID alertId = UUID.randomUUID();
        when(jdbc.update(contains("update alert_events set acknowledged_by_user_id="), eq(actorId), eq(alertId), eq(horseId)))
                .thenReturn(1);

        OffsetDateTime now = OffsetDateTime.now();
        when(jdbc.query(contains("select id,telemetry_session_id"), any(RowMapper.class), eq(alertId), eq(horseId)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(alertId);
                    when(rs.getObject("telemetry_session_id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getLong("telemetry_reading_id")).thenReturn(10L);
                    when(rs.getObject("threshold_id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getString("metric_type")).thenReturn("HEART_RATE");
                    when(rs.getBigDecimal("observed_value")).thenReturn(BigDecimal.valueOf(230));
                    when(rs.getBigDecimal("threshold_value")).thenReturn(BigDecimal.valueOf(220));
                    when(rs.getString("threshold_direction")).thenReturn("MAXIMUM");
                    when(rs.getObject("occurred_at", OffsetDateTime.class)).thenReturn(now);
                    when(rs.getObject("acknowledged_by_user_id", UUID.class)).thenReturn(actorId);
                    when(rs.getObject("acknowledged_at", OffsetDateTime.class)).thenReturn(now);
                    return List.of(mapper.mapRow(rs, 1));
                });

        TelemetryService.AlertView view = service.acknowledge(horseId, alertId, actorId, true, false);

        assertThat(view).isNotNull();
        assertThat(view.id()).isEqualTo(alertId);
        assertThat(view.acknowledgedBy()).isEqualTo(actorId);
        assertThat(view.metricType()).isEqualTo("HEART_RATE");
    }

    @Test
    void thresholdsQueriesActiveAndExpiredCorrectly() {
        when(jdbc.query(contains("from horse_metric_thresholds where horse_id=?"), any(RowMapper.class), eq(horseId)))
                .thenReturn(List.of());

        List<TelemetryService.ThresholdView> active = service.thresholds(horseId, actorId, true, false, false, false, false);
        assertThat(active).isEmpty();
        verify(jdbc).query(contains("and (effective_to is null or effective_to>SYSDATETIMEOFFSET())"), any(RowMapper.class), eq(horseId));

        List<TelemetryService.ThresholdView> all = service.thresholds(horseId, actorId, true, false, false, false, true);
        assertThat(all).isEmpty();
    }
}
