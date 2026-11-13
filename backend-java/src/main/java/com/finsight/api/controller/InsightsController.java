package com.finsight.api.controller;

import com.finsight.api.dto.InsightsDtos.CategorizeRequest;
import com.finsight.api.dto.InsightsDtos.CategorizeResponse;
import com.finsight.api.dto.InsightsDtos.InsightsOverviewResponse;
import com.finsight.api.service.InsightsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/insights")
@Tag(name = "Insights", description = "Forecasting, anomaly detection and auto-categorization")
public class InsightsController {
    private final InsightsService insightsService;

    public InsightsController(InsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @GetMapping("/overview")
    @Operation(summary = "AI overview", description = "Per-category next-month forecast, unusual spending and "
            + "plain-language insights computed by the Python analytics service.")
    public InsightsOverviewResponse overview(@AuthenticationPrincipal UUID userId) {
        return insightsService.overview(userId);
    }

    @PostMapping("/categorize")
    @Operation(summary = "Suggest a category from a description")
    public CategorizeResponse categorize(@AuthenticationPrincipal UUID userId,
                                         @Valid @RequestBody CategorizeRequest request) {
        return insightsService.categorize(userId, request.description());
    }
}
