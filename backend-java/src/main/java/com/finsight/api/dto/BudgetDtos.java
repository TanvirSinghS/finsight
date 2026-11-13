package com.finsight.api.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class BudgetDtos {
    private BudgetDtos() {
    }

    public record BudgetRequest(
            @NotNull UUID categoryId,
            @NotNull LocalDate periodStart,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount) {
    }

    public record BudgetResponse(
            UUID id, UUID categoryId, String categoryName, String categoryColor, LocalDate periodStart,
            BigDecimal amount, BigDecimal spent, BigDecimal remaining, int percentUsed, boolean overBudget,
            boolean nearLimit) {
    }
}
