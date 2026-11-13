package com.finsight.api.service;

import com.finsight.api.domain.User;
import com.finsight.api.dto.InsightsDtos.*;
import com.finsight.api.repository.CategoryRepository;
import com.finsight.api.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class InsightsService {
    private static final int MONTHS_OF_HISTORY = 6;

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final InsightsClient insightsClient;
    private final UserService userService;

    public InsightsService(TransactionRepository transactionRepository, CategoryRepository categoryRepository,
                           InsightsClient insightsClient, UserService userService) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.insightsClient = insightsClient;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public InsightsOverviewResponse overview(UUID userId) {
        User user = userService.get(userId);
        YearMonth current = YearMonth.now();
        YearMonth first = current.minusMonths(MONTHS_OF_HISTORY - 1L);
        LocalDate from = first.atDay(1);

        Map<String, List<InsightsClient.CategoryTotal>> months = new LinkedHashMap<>();
        for (int i = 0; i < MONTHS_OF_HISTORY; i++) {
            months.put(first.plusMonths(i).toString(), new ArrayList<>());
        }
        for (Object[] row : transactionRepository.monthlySpendByCategory(userId, from)) {
            String month = (String) row[2];
            months.computeIfAbsent(month, key -> new ArrayList<>())
                    .add(new InsightsClient.CategoryTotal(row[0].toString(), (String) row[1],
                            toMoney(row[3])));
        }
        List<InsightsClient.CategoryMonth> monthPayload = months.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new InsightsClient.CategoryMonth(entry.getKey(), entry.getValue()))
                .toList();

        List<InsightsClient.InsightsTransaction> transactions = new ArrayList<>();
        for (Object[] row : transactionRepository.expenseRowsForAnomalies(userId, current.minusMonths(6).atDay(1))) {
            if (transactions.size() >= 250) {
                break;
            }
            transactions.add(new InsightsClient.InsightsTransaction(row[0].toString(), toMoney(row[1]),
                    row[2].toString(), (String) row[3], (String) row[4]));
        }

        InsightsClient.PythonInsights result = insightsClient.fetchOverview(
                new InsightsClient.InsightsRequest(monthPayload, transactions, user.getBaseCurrency()));

        if (result == null) {
            return new InsightsOverviewResponse(Instant.now(), false,
                    "The analytics service is waking up or unreachable. Your finances are unaffected — try again in a moment.",
                    List.of(), List.of(), List.of(), null);
        }
        return new InsightsOverviewResponse(Instant.now(), true, null,
                result.forecast() == null ? List.of() : result.forecast(),
                result.anomalies() == null ? List.of() : result.anomalies(),
                result.insights() == null ? List.of() : result.insights(),
                result.meta());
    }

    @Transactional(readOnly = true)
    public CategorizeResponse categorize(UUID userId, String description) {
        List<String> categories = categoryRepository.findByUserIdOrderByKindAscNameAsc(userId).stream()
                .map(category -> category.getName())
                .toList();
        return insightsClient.categorize(description, categories);
    }

    private BigDecimal toMoney(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(((Number) value).doubleValue());
    }
}
