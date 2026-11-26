package com.finsight.api.controller;

import com.finsight.api.dto.DashboardDtos.CashflowResponse;
import com.finsight.api.dto.DashboardDtos.DashboardResponse;
import com.finsight.api.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Aggregated dashboard payload")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "Balances, monthly cashflow, spending split, trend and budget alerts")
    public DashboardResponse get(@AuthenticationPrincipal UUID userId) {
        return dashboardService.get(userId);
    }

    @GetMapping("/cashflow")
    @Operation(summary = "Income vs expenses over 1w, 1m, 3m, 6m or 1y")
    public CashflowResponse cashflow(@AuthenticationPrincipal UUID userId,
                                     @RequestParam(required = false) String range) {
        return dashboardService.cashflow(userId, range);
    }
}
