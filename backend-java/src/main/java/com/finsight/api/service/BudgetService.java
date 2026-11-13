package com.finsight.api.service;

import com.finsight.api.domain.*;
import com.finsight.api.dto.BudgetDtos.BudgetRequest;
import com.finsight.api.dto.BudgetDtos.BudgetResponse;
import com.finsight.api.exception.ApiException;
import com.finsight.api.repository.BudgetRepository;
import com.finsight.api.repository.CategoryRepository;
import com.finsight.api.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
public class BudgetService {
    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UserService userService;

    public BudgetService(BudgetRepository budgetRepository, CategoryRepository categoryRepository,
                         TransactionRepository transactionRepository, UserService userService) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> list(UUID userId, String month) {
        YearMonth period = parseMonth(month);
        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();
        return budgetRepository.findByUserIdAndPeriodStartOrderByCategoryNameAsc(userId, start).stream()
                .map(budget -> toResponse(budget, spentFor(userId, budget.getCategory().getId(), start, end)))
                .toList();
    }

    @Transactional
    public BudgetResponse create(UUID userId, BudgetRequest request) {
        User user = userService.get(userId);
        Category category = requireExpenseCategory(userId, request.categoryId());
        LocalDate periodStart = normalizePeriod(request.periodStart());
        if (budgetRepository.existsByUserIdAndCategoryIdAndPeriodStart(userId, category.getId(), periodStart)) {
            throw ApiException.conflict("A budget for " + category.getName() + " already exists this month.");
        }
        Budget budget = new Budget(user, category, periodStart, request.amount());
        budgetRepository.save(budget);
        YearMonth period = YearMonth.from(periodStart);
        return toResponse(budget, spentFor(userId, category.getId(), period.atDay(1), period.atEndOfMonth()));
    }

    @Transactional
    public BudgetResponse update(UUID userId, UUID id, BudgetRequest request) {
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Budget not found."));
        Category category = requireExpenseCategory(userId, request.categoryId());
        LocalDate periodStart = normalizePeriod(request.periodStart());
        boolean moved = !budget.getCategory().getId().equals(category.getId())
                || !budget.getPeriodStart().equals(periodStart);
        if (moved && budgetRepository.existsByUserIdAndCategoryIdAndPeriodStart(userId, category.getId(), periodStart)) {
            throw ApiException.conflict("A budget for that category already exists this month.");
        }
        budget.setCategory(category);
        budget.setPeriodStart(periodStart);
        budget.setAmount(request.amount());
        budgetRepository.save(budget);
        YearMonth period = YearMonth.from(periodStart);
        return toResponse(budget, spentFor(userId, category.getId(), period.atDay(1), period.atEndOfMonth()));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Budget not found."));
        budgetRepository.delete(budget);
    }

    private Category requireExpenseCategory(UUID userId, UUID categoryId) {
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> ApiException.badRequest("Category not found."));
        if (category.getKind() != CategoryKind.EXPENSE) {
            throw ApiException.badRequest("Budgets can only track expense categories.");
        }
        return category;
    }

    private BigDecimal spentFor(UUID userId, UUID categoryId, LocalDate start, LocalDate end) {
        return transactionRepository.sumByCategory(userId, TransactionType.EXPENSE, categoryId, start, end)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static BudgetResponse toResponse(Budget budget, BigDecimal spent) {
        BigDecimal amount = budget.getAmount();
        BigDecimal remaining = amount.subtract(spent);
        int percent = amount.signum() == 0 ? 0
                : spent.multiply(BigDecimal.valueOf(100)).divide(amount, 0, RoundingMode.HALF_UP).intValue();
        boolean over = spent.compareTo(amount) > 0;
        return new BudgetResponse(budget.getId(), budget.getCategory().getId(), budget.getCategory().getName(),
                budget.getCategory().getColor(), budget.getPeriodStart(), amount, spent, remaining, percent,
                over, percent >= 80 && !over);
    }

    public static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest("Month must be in YYYY-MM format.");
        }
    }

    private LocalDate normalizePeriod(LocalDate date) {
        return date.withDayOfMonth(1);
    }
}
