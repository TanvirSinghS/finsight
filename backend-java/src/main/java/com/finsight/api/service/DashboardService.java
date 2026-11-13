package com.finsight.api.service;

import com.finsight.api.domain.TransactionType;
import com.finsight.api.domain.User;
import com.finsight.api.dto.AccountDtos.AccountResponse;
import com.finsight.api.dto.BudgetDtos.BudgetResponse;
import com.finsight.api.dto.DashboardDtos.*;
import com.finsight.api.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class DashboardService {
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final BudgetService budgetService;
    private final UserService userService;

    public DashboardService(TransactionRepository transactionRepository, AccountService accountService,
                            BudgetService budgetService, UserService userService) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.budgetService = budgetService;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse get(UUID userId) {
        User user = userService.get(userId);
        YearMonth current = YearMonth.now();
        LocalDate start = current.atDay(1);
        LocalDate end = current.atEndOfMonth();

        List<AccountResponse> accounts = accountService.list(userId);
        BigDecimal totalBalance = accounts.stream().map(AccountResponse::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal income = money(transactionRepository.sumByType(userId, TransactionType.INCOME, start, end));
        BigDecimal expense = money(transactionRepository.sumByType(userId, TransactionType.EXPENSE, start, end));
        BigDecimal net = income.subtract(expense);
        double savingsRate = income.signum() == 0 ? 0
                : net.multiply(BigDecimal.valueOf(100)).divide(income, 1, RoundingMode.HALF_UP).doubleValue();

        List<CategorySpend> byCategory = spendingByCategory(userId, start, end, expense);
        List<CategorySpend> topCategories = byCategory.stream().limit(5).toList();

        List<MonthlyPoint> trend = monthlyTrend(userId, current.minusMonths(5));
        List<BudgetAlert> alerts = budgetService.list(userId, current.toString()).stream()
                .filter(b -> b.percentUsed() >= 80)
                .map(this::toAlert)
                .toList();

        return new DashboardResponse(totalBalance, income, expense, net, savingsRate, user.getBaseCurrency(),
                accounts.size(), byCategory, topCategories, trend, alerts,
                transactionRepository.findTop8ByUserIdOrderByOccurredOnDescCreatedAtDesc(userId).stream()
                        .map(TransactionService::toResponse).toList());
    }

    private List<CategorySpend> spendingByCategory(UUID userId, LocalDate start, LocalDate end, BigDecimal total) {
        List<CategorySpend> result = new ArrayList<>();
        for (Object[] row : transactionRepository.spendingByCategory(userId, start, end)) {
            BigDecimal amount = money(row[3]);
            double percent = total.signum() == 0 ? 0
                    : amount.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).doubleValue();
            result.add(new CategorySpend(UUID.fromString(row[0].toString()), (String) row[1], (String) row[2],
                    amount, percent));
        }
        return result;
    }

    private List<MonthlyPoint> monthlyTrend(UUID userId, YearMonth firstMonth) {
        Map<String, MonthlyPoint> points = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            points.put(firstMonth.plusMonths(i).toString(),
                    new MonthlyPoint(firstMonth.plusMonths(i).toString(), BigDecimal.ZERO, BigDecimal.ZERO));
        }
        for (Object[] row : transactionRepository.monthlyCashflow(userId, firstMonth.atDay(1))) {
            points.put((String) row[0], new MonthlyPoint((String) row[0], money(row[1]), money(row[2])));
        }
        return new ArrayList<>(points.values());
    }

    private BudgetAlert toAlert(BudgetResponse budget) {
        return new BudgetAlert(budget.id(), budget.categoryId(), budget.categoryName(), budget.amount(),
                budget.spent(), budget.percentUsed(), budget.overBudget());
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
}
