package com.finsight.api.service;

import com.finsight.api.dto.InsightsDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Thin client for the FastAPI analytics service. Every failure degrades gracefully:
 * the dashboard keeps working, the insights page shows a friendly disclaimer.
 */
@Component
public class InsightsClient {
    private static final Logger log = LoggerFactory.getLogger(InsightsClient.class);

    private final RestClient client;

    public InsightsClient(@Qualifier("insightsRestClient") RestClient client) {
        this.client = client;
    }

    public record CategoryTotal(String categoryId, String name, BigDecimal total) {
    }

    public record CategoryMonth(String month, List<CategoryTotal> categories) {
    }

    public record InsightsTransaction(String transactionId, BigDecimal amount, String occurredOn,
                                      String description, String category) {
    }

    public record InsightsRequest(List<CategoryMonth> months, List<InsightsTransaction> transactions,
                                  String currency) {
    }

    public record PythonInsights(List<ForecastItem> forecast, List<AnomalyItem> anomalies,
                                 List<InsightItem> insights, AiMeta meta) {
    }

    public record CategorizeRequest(String description, List<String> categories) {
    }

    public PythonInsights fetchOverview(InsightsRequest request) {
        try {
            return client.post().uri("/insights/overview").body(request).retrieve().body(PythonInsights.class);
        } catch (RestClientException ex) {
            log.warn("Insights service unavailable: {}", ex.getMessage());
            return null;
        }
    }

    public CategorizeResponse categorize(String description, List<String> categories) {
        try {
            CategorizeResponse response = client.post().uri("/insights/categorize")
                    .body(new CategorizeRequest(description, categories))
                    .retrieve().body(CategorizeResponse.class);
            return response == null
                    ? new CategorizeResponse(null, 0, List.of(), "offline")
                    : response;
        } catch (RestClientException ex) {
            log.warn("Categorize call failed: {}", ex.getMessage());
            return new CategorizeResponse(null, 0, List.of(), "offline");
        }
    }
}
