package com.finsight.api.service;

import com.finsight.api.domain.*;
import com.finsight.api.dto.BudgetDtos.BudgetResponse;
import com.finsight.api.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetServiceTest {

    private final User user = new User("alex@example.com", "hash", "Alex", "USD");
    private final Category category = new Category(user, "Groceries", CategoryKind.EXPENSE, "#22c55e", "cart");

    @Test
    void marksBudgetOverLimitWhenSpendExceedsAmount() {
        Budget budget = new Budget(user, category, LocalDate.now().withDayOfMonth(1), new BigDecimal("100.00"));

        BudgetResponse response = BudgetService.toResponse(budget, new BigDecimal("120.00"));

        assertThat(response.overBudget()).isTrue();
        assertThat(response.nearLimit()).isFalse();
        assertThat(response.percentUsed()).isEqualTo(120);
        assertThat(response.remaining()).isEqualByComparingTo("-20.00");
    }

    @Test
    void flagsNearLimitAtEightyPercent() {
        Budget budget = new Budget(user, category, LocalDate.now().withDayOfMonth(1), new BigDecimal("200.00"));

        BudgetResponse response = BudgetService.toResponse(budget, new BigDecimal("170.00"));

        assertThat(response.nearLimit()).isTrue();
        assertThat(response.overBudget()).isFalse();
        assertThat(response.percentUsed()).isEqualTo(85);
    }

    @Test
    void parsesMonthAndRejectsGarbage() {
        assertThat(BudgetService.parseMonth("2026-03")).isEqualTo(YearMonth.of(2026, 3));
        assertThat(BudgetService.parseMonth(null)).isEqualTo(YearMonth.now());
        assertThatThrownBy(() -> BudgetService.parseMonth("March"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("YYYY-MM");
    }
}
