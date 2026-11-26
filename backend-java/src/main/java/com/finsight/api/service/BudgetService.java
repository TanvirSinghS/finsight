package com.finsight.api.service;

import com.finsight.api.domain.*;
import com.finsight.api.dto.BudgetDtos.BudgetHistoryEntry;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        return listForPeriod(userId, period, spendByCategory(userId, period));
    }

    /**
     * Builds the month's budget rows from a spend map loaded in one grouped query, so
     * the page costs two queries no matter how many budgets the user has.
     */
    @Transactional(readOnly = true)
    public List<BudgetResponse> listForPeriod(UUID userId, YearMonth period, Map<UUID, BigDecimal> spentByCategory) {
        LocalDate start = period.atDay(1);
        return budgetRepository.findByUserIdAndPeriodStartOrderByCategoryNameAsc(userId, start).stream()
                .map(budget -> toResponse(budget, spentByCategory.getOrDefault(budget.getCategory().getId(),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))))
                .toList();
    }

    private Map<UUID, BigDecimal> spendByCategory(UUID userId, YearMonth period) {
        Map<UUID, BigDecimal> spend = new HashMap<>();
        for (Object[] row : transactionRepository.sumByCategoryGrouped(userId, TransactionType.EXPENSE,
                period.atDay(1), period.atEndOfMonth())) {
            spend.put((UUID) row[0], ((BigDecimal) row[1]).setScale(2, RoundingMode.HALF_UP));
        }
        return spend;
    }

    /**
     * Past months' budgets against actual spend, newest first. Only months that had
     * at least one budget appear, so the page shows real history instead of gaps.
     */
    @Transactional(readOnly = true)
    public List<BudgetHistoryEntry> history(UUID userId, Integer months) {
        int count = months == null ? 6 : Math.max(1, Math.min(24, months));
        YearMonth current = YearMonth.now();
        YearMonth first = current.minusMonths(count);
        YearMonth last = current.minusMonths(1);

        List<Budget> budgets = budgetRepository.findByUserIdAndPeriodStartBetweenOrderByPeriodStartAsc(
                userId, first.atDay(1), last.atEndOfMonth());

        Map<String, Map<UUID, BigDecimal>> spendByMonth = new HashMap<>();
        for (Object[] row : transactionRepository.monthlySpendByCategory(userId, first.atDay(1))) {
            String month = (String) row[2];
            if (month.compareTo(last.toString()) > 0) {
                continue;
            }
            spendByMonth.computeIfAbsent(month, key -> new HashMap<>())
                    .put((UUID) row[0], money(row[3]));
        }

        Map<YearMonth, List<Budget>> grouped = new HashMap<>();
        for (Budget budget : budgets) {
            grouped.computeIfAbsent(YearMonth.from(budget.getPeriodStart()), key -> new ArrayList<>()).add(budget);
        }

        List<BudgetHistoryEntry> entries = new ArrayList<>();
        for (YearMonth month = last; !month.isBefore(first); month = month.minusMonths(1)) {
            List<Budget> monthBudgets = grouped.get(month);
            if (monthBudgets == null || monthBudgets.isEmpty()) {
                continue;
            }
            Map<UUID, BigDecimal> spend = spendByMonth.getOrDefault(month.toString(), Map.of());
            BigDecimal budgeted = BigDecimal.ZERO;
            BigDecimal spent = BigDecimal.ZERO;
            int overCount = 0;
            List<String> overCategories = new ArrayList<>();
            for (Budget budget : monthBudgets) {
                BigDecimal categorySpend = spend.getOrDefault(budget.getCategory().getId(), money(null));
                budgeted = budgeted.add(budget.getAmount());
                spent = spent.add(categorySpend);
                if (categorySpend.compareTo(budget.getAmount()) > 0) {
                    overCount++;
                    overCategories.add(budget.getCategory().getName());
                }
            }
            budgeted = budgeted.setScale(2, RoundingMode.HALF_UP);
            spent = spent.setScale(2, RoundingMode.HALF_UP);
            BigDecimal remaining = budgeted.subtract(spent);
            int percent = budgeted.signum() == 0 ? 0
                    : spent.multiply(BigDecimal.valueOf(100)).divide(budgeted, 0, RoundingMode.HALF_UP).intValue();
            entries.add(new BudgetHistoryEntry(month.toString(), budgeted, spent, remaining, percent,
                    overCount == 0, monthBudgets.size(), overCount, List.copyOf(overCategories)));
        }
        return entries;
    }

    private BigDecimal money(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(((Number) value).doubleValue()).setScale(2, RoundingMode.HALF_UP);
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
