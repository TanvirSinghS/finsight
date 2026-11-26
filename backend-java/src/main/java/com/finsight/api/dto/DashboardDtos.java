package com.finsight.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DashboardDtos {
    private DashboardDtos() {
    }

    public record CategorySpend(UUID categoryId, String name, String color, BigDecimal total, double percent) {
    }

    public record MonthlyPoint(String month, BigDecimal income, BigDecimal expenses) {
    }

    public record CashflowPoint(String label, BigDecimal income, BigDecimal expenses) {
    }

    public record CashflowResponse(String range, LocalDate from, LocalDate to, List<CashflowPoint> points) {
    }

    public record BudgetAlert(UUID budgetId, UUID categoryId, String categoryName, BigDecimal amount,
                              BigDecimal spent, int percentUsed, boolean overBudget) {
    }

    public record DashboardResponse(
            BigDecimal totalBalance,
            BigDecimal monthlyIncome,
            BigDecimal monthlyExpense,
            BigDecimal netThisMonth,
            double savingsRate,
            String currency,
            int accountCount,
            List<CategorySpend> spendingByCategory,
            List<CategorySpend> topCategories,
            List<MonthlyPoint> monthlyTrend,
            List<BudgetAlert> budgetAlerts,
            List<TransactionDtos.TransactionResponse> recentTransactions) {
    }
}
