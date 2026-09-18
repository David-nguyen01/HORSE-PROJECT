package com.racehorse.reporting.service;

import com.racehorse.reporting.dto.DashboardResponse;
import com.racehorse.reporting.dto.DashboardDrilldownResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DashboardService {

    private final JdbcTemplate jdbc;

    public DashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(LocalDate startDate, LocalDate endDate, UUID stableId) {
        LocalDate start = (startDate != null) ? startDate : LocalDate.of(1900, 1, 1);
        LocalDate end = (endDate != null) ? endDate : LocalDate.now();

        // 1. Financials
        String finSql = """
                select transaction_type, category, sum(amount) as total
                from financial_transactions
                where transaction_date >= coalesce(?, '1900-01-01')
                  and transaction_date <= coalesce(?, '2100-01-01')
                  and (? is null or stable_id = ?)
                group by transaction_type, category
                """;

        BigDecimal[] totalRevenue = {BigDecimal.ZERO};
        BigDecimal[] totalExpense = {BigDecimal.ZERO};
        Map<String, BigDecimal> expensesByCategory = new HashMap<>();

        jdbc.query(finSql, rs -> {
            String type = rs.getString("transaction_type");
            String cat = rs.getString("category");
            BigDecimal total = rs.getBigDecimal("total");

            if ("REVENUE".equals(type)) {
                totalRevenue[0] = totalRevenue[0].add(total);
            } else {
                totalExpense[0] = totalExpense[0].add(total);
                expensesByCategory.put(cat, total);
            }
        }, start, end, stableId, stableId);

        // 2. Race Events
        Long raceEvents = jdbc.queryForObject("""
                select count(*)
                from race_events
                where race_date >= coalesce(?, '1900-01-01')
                  and race_date <= coalesce(?, '2100-01-01')
                """, Long.class, start, end);

        // 3. Training Evaluations
        Long evaluations = jdbc.queryForObject("""
                select count(*)
                from training_evaluations
                where session_date >= coalesce(?, '1900-01-01')
                  and session_date <= coalesce(?, '2100-01-01')
                """, Long.class, start, end);

        return new DashboardResponse(
                totalRevenue[0],
                totalExpense[0],
                raceEvents != null ? raceEvents : 0L,
                evaluations != null ? evaluations : 0L,
                expensesByCategory
        );
    }

    @Transactional(readOnly = true)
    public Page<DashboardDrilldownResponse.DrilldownRow> getDrilldown(
            String section, LocalDate startDate, LocalDate endDate, UUID stableId, Pageable pageable) {

        LocalDate start = (startDate != null) ? startDate : LocalDate.of(1900, 1, 1);
        LocalDate end = (endDate != null) ? endDate : LocalDate.now();

        return switch (section.toUpperCase()) {
            case "FINANCIAL" -> drilldownFinancial(start, end, stableId, pageable);
            case "RACE_EVENTS" -> drilldownRaceEvents(start, end, pageable);
            case "TRAINING_EVALUATIONS" -> drilldownTrainingEvaluations(start, end, pageable);
            default -> new PageImpl<>(List.of(), pageable, 0);
        };
    }

    private Page<DashboardDrilldownResponse.DrilldownRow> drilldownFinancial(
            LocalDate startDate, LocalDate endDate, UUID stableId, Pageable pageable) {

        Long total = jdbc.queryForObject("""
                select count(*) from financial_transactions
                where transaction_date >= coalesce(?, '1900-01-01')
                  and transaction_date <= coalesce(?, '2100-01-01')
                  and (? is null or stable_id = ?)
                """, Long.class, startDate, endDate, stableId, stableId);

        List<DashboardDrilldownResponse.DrilldownRow> rows = jdbc.query("""
                select id, transaction_type as col1, category as col2,
                       cast(amount as varchar) as col3, convert(varchar(10), transaction_date, 23) as col4
                from financial_transactions
                where transaction_date >= coalesce(?, '1900-01-01')
                  and transaction_date <= coalesce(?, '2100-01-01')
                  and (? is null or stable_id = ?)
                order by transaction_date desc
                offset ? rows fetch next ? rows only
                """, (rs, rowNum) -> new DashboardDrilldownResponse.DrilldownRow(
                rs.getObject("id", UUID.class),
                rs.getString("col1"),
                rs.getString("col2"),
                rs.getString("col3"),
                rs.getString("col4")
        ), startDate, endDate, stableId, stableId, pageable.getOffset(), pageable.getPageSize());

        return new PageImpl<>(rows, pageable, total != null ? total : 0);
    }

    private Page<DashboardDrilldownResponse.DrilldownRow> drilldownRaceEvents(
            LocalDate startDate, LocalDate endDate, Pageable pageable) {

        Long total = jdbc.queryForObject("""
                select count(*) from race_events
                where race_date >= coalesce(?, '1900-01-01')
                  and race_date <= coalesce(?, '2100-01-01')
                """, Long.class, startDate, endDate);

        List<DashboardDrilldownResponse.DrilldownRow> rows = jdbc.query("""
                select id, race_name as col1, racecourse as col2,
                       race_class as col3, convert(varchar(10), race_date, 23) as col4
                from race_events
                where race_date >= coalesce(?, '1900-01-01')
                  and race_date <= coalesce(?, '2100-01-01')
                order by race_date desc
                offset ? rows fetch next ? rows only
                """, (rs, rowNum) -> new DashboardDrilldownResponse.DrilldownRow(
                rs.getObject("id", UUID.class),
                rs.getString("col1"),
                rs.getString("col2"),
                rs.getString("col3"),
                rs.getString("col4")
        ), startDate, endDate, pageable.getOffset(), pageable.getPageSize());

        return new PageImpl<>(rows, pageable, total != null ? total : 0);
    }

    private Page<DashboardDrilldownResponse.DrilldownRow> drilldownTrainingEvaluations(
            LocalDate startDate, LocalDate endDate, Pageable pageable) {

        Long total = jdbc.queryForObject("""
                select count(*) from training_evaluations
                where session_date >= coalesce(?, '1900-01-01')
                  and session_date <= coalesce(?, '2100-01-01')
                """, Long.class, startDate, endDate);

        List<DashboardDrilldownResponse.DrilldownRow> rows = jdbc.query("""
                select id, task_name as col1, score as col2,
                       notes as col3, convert(varchar(10), session_date, 23) as col4
                from training_evaluations
                where session_date >= coalesce(?, '1900-01-01')
                  and session_date <= coalesce(?, '2100-01-01')
                order by session_date desc
                offset ? rows fetch next ? rows only
                """, (rs, rowNum) -> new DashboardDrilldownResponse.DrilldownRow(
                rs.getObject("id", UUID.class),
                rs.getString("col1"),
                rs.getString("col2"),
                rs.getString("col3"),
                rs.getString("col4")
        ), startDate, endDate, pageable.getOffset(), pageable.getPageSize());

        return new PageImpl<>(rows, pageable, total != null ? total : 0);
    }

    public String exportDashboardHtml(LocalDate startDate, LocalDate endDate, UUID stableId) {
        DashboardResponse dash = getDashboard(startDate, endDate, stableId);
        return """
                <html>
                <head><title>Dashboard Export</title></head>
                <body>
                    <h1>Dashboard Export</h1>
                    <p>Total Revenue: %s</p>
                    <p>Total Expense: %s</p>
                    <p>Race Events: %d</p>
                    <p>Training Evaluations: %d</p>
                </body>
                </html>
                """.formatted(dash.totalRevenue(), dash.totalExpense(), dash.totalRaceEvents(), dash.totalTrainingEvaluations());
    }
}
