package com.racehorse.health.service;

import com.racehorse.health.dto.StableMapResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * FR08 – queries all active horses with their current health status and stable location.
 */
@Service
public class StableMapService {

    private final JdbcTemplate jdbc;

    public StableMapService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns all stalls that have an active (or resting/injured) horse assigned,
     * optionally filtered by health status.
     *
     * @param statusFilter one of FIT | MONITOR | INJURED | QUARANTINED, or null for all
     */
    @Transactional(readOnly = true)
    public StableMapResponse stableMap(String statusFilter) {
        boolean hasFilter = statusFilter != null && !statusFilter.isBlank();

        String sql = """
                SELECT h.id, h.name, h.registration_code, h.stable_location, h.health_status
                FROM dbo.horses h
                WHERE h.status IN ('ACTIVE', 'RESTING', 'INJURED')
                  AND h.stable_location IS NOT NULL
                """
                + (hasFilter ? " AND h.health_status = ?" : "")
                + " ORDER BY h.stable_location ASC, h.name ASC";

        List<StableMapResponse.StallEntry> entries = hasFilter
                ? jdbc.query(sql, this::mapRow, statusFilter.trim().toUpperCase())
                : jdbc.query(sql, this::mapRow);

        return new StableMapResponse(entries);
    }

    private StableMapResponse.StallEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new StableMapResponse.StallEntry(
                rs.getString("stable_location"),
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("registration_code"),
                rs.getString("health_status"));
    }
}
