package com.racehorse.reporting.dto;

import java.math.BigDecimal;
import java.util.Map;

public record DashboardResponse(
        BigDecimal totalRevenue,
        BigDecimal totalExpense,
        long totalRaceEvents,
        long totalTrainingEvaluations,
        Map<String, BigDecimal> expensesByCategory
) {}
