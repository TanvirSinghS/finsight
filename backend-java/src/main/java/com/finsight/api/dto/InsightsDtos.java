package com.finsight.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class InsightsDtos {
    private InsightsDtos() {
    }

    public record ForecastItem(
            String categoryId, String category, BigDecimal lastMonth, BigDecimal forecastNextMonth,
            BigDecimal changePercent, String trend, BigDecimal confidence, String method) {
    }

    public record AnomalyItem(
            String transactionId, String description, BigDecimal amount, String occurredOn, String category,
            String reason, String severity, BigDecimal zScore) {
    }

    public record InsightItem(String text, String type, String severity, String category) {
    }

    public record AiMeta(String model, int monthsAnalysed, int transactionsAnalysed, String generatedAt) {
    }

    public record InsightsOverviewResponse(
            Instant generatedAt,
            boolean available,
            String message,
            List<ForecastItem> forecast,
            List<AnomalyItem> anomalies,
            List<InsightItem> insights,
            AiMeta meta) {
    }

    public record CategorizeRequest(@NotBlank @Size(max = 255) String description) {
    }

    public record CategorizeResponse(String category, double confidence, List<String> alternatives, String method) {
    }
}
