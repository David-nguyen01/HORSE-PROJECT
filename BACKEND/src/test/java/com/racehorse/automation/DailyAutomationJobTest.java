package com.racehorse.automation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DailyAutomationJobTest {

    @Mock private JdbcTemplate jdbc;
    private DailyAutomationJob job;

    @BeforeEach
    void setUp() {
        job = new DailyAutomationJob(jdbc, "UTC");
    }

    @Test
    void remindPreventiveCareIncludesBothVeterinarianAndHeadTrainer() {
        LocalDate today = LocalDate.of(2026, 9, 20);

        job.remindPreventiveCare(today);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sqlCaptor.capture(), eq(today), eq(today));

        String executedSql = sqlCaptor.getValue();
        assertThat(executedSql).contains("ur.role in ('VETERINARIAN', 'HEAD_TRAINER')");
        assertThat(executedSql).contains("u.enabled = 1");
        assertThat(executedSql).contains("PREVENTIVE_CARE_DUE");
    }
}
