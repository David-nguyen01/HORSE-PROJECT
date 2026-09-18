package com.racehorse.reporting.dto;

import org.springframework.data.domain.Page;

import java.util.UUID;

public record DashboardDrilldownResponse(
        String section,
        Page<DrilldownRow> rows
) {
    public record DrilldownRow(
            UUID id,
            String col1,
            String col2,
            String col3,
            String col4
    ) {}
}
