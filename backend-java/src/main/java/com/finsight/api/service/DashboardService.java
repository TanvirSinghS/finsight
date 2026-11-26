package com.finsight.api.service;

import com.finsight.api.domain.TransactionType;
import com.finsight.api.domain.User;
import com.finsight.api.dto.AccountDtos.AccountResponse;
import com.finsight.api.dto.BudgetDtos.BudgetResponse;
import com.finsight.api.dto.DashboardDtos.*;
import com.finsight.api.exception.ApiException;
import com.finsight.api.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DashboardService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

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

        List<AccountResponse> accounts = accountService.list(user);
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
        Map<UUID, BigDecimal> spentByCategory = new HashMap<>();
        byCategory.forEach(spend -> spentByCategory.put(spend.categoryId(), spend.total()));
        List<BudgetAlert> alerts = budgetService.listForPeriod(userId, current, spentByCategory).stream()
                .filter(b -> b.percentUsed() >= 60)
                .sorted(Comparator.comparingInt(BudgetResponse::percentUsed).reversed())
                .map(this::toAlert)
                .toList();

        return new DashboardResponse(totalBalance, income, expense, net, savingsRate, user.getBaseCurrency(),
                accounts.size(), byCategory, topCategories, trend, alerts,
                transactionRepository.findTop8ByUserIdOrderByOccurredOnDescCreatedAtDesc(userId).stream()
                        .map(TransactionService::toResponse).toList());
    }

    /**
     * Cashflow series for the dashboard range picker. Short ranges bucket by day or
     * week, longer ranges by month — each bucket carries income and expenses only,
     * so the chart never has to aggregate on the client.
     */
    @Transactional(readOnly = true)
    public CashflowResponse cashflow(UUID userId, String range) {
        String key = range == null || range.isBlank() ? "6m" : range.trim().toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now();
        YearMonth current = YearMonth.from(today);
        return switch (key) {
            case "1w" -> new CashflowResponse(key, today.minusDays(6), today,
                    dailyPoints(userId, today.minusDays(6), today,
                            DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)));
            case "1m" -> new CashflowResponse(key, today.minusDays(29), today,
                    dailyPoints(userId, today.minusDays(29), today,
                            DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)));
            case "3m" -> new CashflowResponse(key, today.minusWeeks(12), today,
                    weeklyPoints(userId, today.minusWeeks(12), today));
            case "6m" -> new CashflowResponse(key, current.minusMonths(5).atDay(1), today,
                    monthlyPoints(userId, current.minusMonths(5), current, "MMM"));
            case "1y" -> new CashflowResponse(key, current.minusMonths(11).atDay(1), today,
                    monthlyPoints(userId, current.minusMonths(11), current, "MMM yy"));
            default -> throw ApiException.badRequest("Range must be one of 1w, 1m, 3m, 6m, 1y.");
        };
    }

    private List<CashflowPoint> dailyPoints(UUID userId, LocalDate from, LocalDate to, DateTimeFormatter format) {
        Map<LocalDate, BigDecimal[]> byDay = cashflowByDay(userId, from, to);
        List<CashflowPoint> points = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            BigDecimal[] values = byDay.get(day);
            points.add(new CashflowPoint(day.format(format),
                    values == null ? ZERO : values[0], values == null ? ZERO : values[1]));
        }
        return points;
    }

    private List<CashflowPoint> weeklyPoints(UUID userId, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal[]> byDay = cashflowByDay(userId, from, to);
        DateTimeFormatter format = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
        List<CashflowPoint> points = new ArrayList<>();
        for (LocalDate weekStart = from; !weekStart.isAfter(to); weekStart = weekStart.plusWeeks(1)) {
            LocalDate weekEnd = weekStart.plusDays(6).isAfter(to) ? to : weekStart.plusDays(6);
            BigDecimal income = ZERO;
            BigDecimal expenses = ZERO;
            for (LocalDate day = weekStart; !day.isAfter(weekEnd); day = day.plusDays(1)) {
                BigDecimal[] values = byDay.get(day);
                if (values != null) {
                    income = income.add(values[0]);
                    expenses = expenses.add(values[1]);
                }
            }
            points.add(new CashflowPoint(weekStart.format(format),
                    income.setScale(2, RoundingMode.HALF_UP), expenses.setScale(2, RoundingMode.HALF_UP)));
        }
        return points;
    }

    private List<CashflowPoint> monthlyPoints(UUID userId, YearMonth first, YearMonth last, String pattern) {
        Map<String, BigDecimal[]> byMonth = new HashMap<>();
        for (Object[] row : transactionRepository.monthlyCashflow(userId, first.atDay(1))) {
            byMonth.put((String) row[0], new BigDecimal[]{money(row[1]), money(row[2])});
        }
        DateTimeFormatter format = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
        List<CashflowPoint> points = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            BigDecimal[] values = byMonth.get(month.toString());
            points.add(new CashflowPoint(month.atDay(1).format(format),
                    values == null ? ZERO : values[0], values == null ? ZERO : values[1]));
        }
        return points;
    }

    private Map<LocalDate, BigDecimal[]> cashflowByDay(UUID userId, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal[]> byDay = new HashMap<>();
        for (Object[] row : transactionRepository.dailyCashflow(userId, from, to)) {
            byDay.put(LocalDate.parse((String) row[0]), new BigDecimal[]{money(row[1]), money(row[2])});
        }
        return byDay;
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
