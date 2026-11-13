package com.finsight.api.service;

import com.finsight.api.domain.*;
import com.finsight.api.dto.RecurringDtos.RecurringRequest;
import com.finsight.api.dto.RecurringDtos.RecurringResponse;
import com.finsight.api.exception.ApiException;
import com.finsight.api.repository.AccountRepository;
import com.finsight.api.repository.CategoryRepository;
import com.finsight.api.repository.RecurringTransactionRepository;
import com.finsight.api.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class RecurringService {
    private static final Logger log = LoggerFactory.getLogger(RecurringService.class);

    private final RecurringTransactionRepository recurringRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final UserService userService;
    private final CurrencyService currencyService;

    public RecurringService(RecurringTransactionRepository recurringRepository,
                            TransactionRepository transactionRepository, AccountRepository accountRepository,
                            CategoryRepository categoryRepository, UserService userService,
                            CurrencyService currencyService) {
        this.recurringRepository = recurringRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.userService = userService;
        this.currencyService = currencyService;
    }

    @Transactional(readOnly = true)
    public List<RecurringResponse> list(UUID userId) {
        return recurringRepository.findByUserIdOrderByNextRunDateAsc(userId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public RecurringResponse create(UUID userId, RecurringRequest request) {
        User user = userService.get(userId);
        RecurringTransaction recurring = new RecurringTransaction();
        recurring.setUser(user);
        apply(recurring, userId, user, request);
        recurring.setActive(request.active() == null || request.active());
        return toResponse(recurringRepository.save(recurring));
    }

    @Transactional
    public RecurringResponse update(UUID userId, UUID id, RecurringRequest request) {
        RecurringTransaction recurring = findOwned(userId, id);
        User user = recurring.getUser();
        apply(recurring, userId, user, request);
        if (request.active() != null) {
            recurring.setActive(request.active());
        }
        return toResponse(recurringRepository.save(recurring));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        recurringRepository.delete(findOwned(userId, id));
    }

    /** Posts any due recurring transactions for one user (used by the dashboard button). */
    @Transactional
    public int runDueForUser(UUID userId) {
        LocalDate today = LocalDate.now();
        List<RecurringTransaction> due = recurringRepository.findByUserIdOrderByNextRunDateAsc(userId).stream()
                .filter(r -> r.isActive() && !r.getNextRunDate().isAfter(today))
                .toList();
        int created = 0;
        for (RecurringTransaction recurring : due) {
            created += post(recurring, today);
        }
        return created;
    }

    /** Daily sweep at 06:00 UTC across every user. */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void runDailySweep() {
        LocalDate today = LocalDate.now();
        List<RecurringTransaction> due = recurringRepository.findDue(today);
        int created = 0;
        for (RecurringTransaction recurring : due) {
            try {
                created += post(recurring, today);
            } catch (RuntimeException ex) {
                log.error("Recurring {} failed: {}", recurring.getId(), ex.getMessage());
            }
        }
        if (created > 0) {
            log.info("Scheduled sweep created {} recurring transactions", created);
        }
    }

    private int post(RecurringTransaction recurring, LocalDate today) {
        int created = 0;
        while (!recurring.getNextRunDate().isAfter(today) && created < 60) {
            Transaction transaction = new Transaction();
            transaction.setUser(recurring.getUser());
            transaction.setAccount(recurring.getAccount());
            transaction.setCategory(recurring.getCategory());
            transaction.setType(recurring.getType());
            transaction.setAmount(recurring.getAmount());
            transaction.setCurrency(recurring.getCurrency());
            transaction.setExchangeRate(recurring.getExchangeRate());
            transaction.setAmountBase(recurring.getAmount().multiply(recurring.getExchangeRate())
                    .setScale(2, RoundingMode.HALF_UP));
            transaction.setDescription(recurring.getDescription());
            transaction.setNotes("Auto-posted recurring transaction");
            transaction.setOccurredOn(recurring.getNextRunDate());
            transaction.setRecurringId(recurring.getId());
            transactionRepository.save(transaction);
            recurring.setNextRunDate(nextDate(recurring.getNextRunDate(), recurring.getFrequency()));
            created++;
        }
        recurringRepository.save(recurring);
        return created;
    }

    public static LocalDate nextDate(LocalDate date, Frequency frequency) {
        return switch (frequency) {
            case DAILY -> date.plusDays(1);
            case WEEKLY -> date.plusWeeks(1);
            case MONTHLY -> date.plusMonths(1);
        };
    }

    private void apply(RecurringTransaction recurring, UUID userId, User user, RecurringRequest request) {
        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> ApiException.badRequest("Select one of your own accounts."));
        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                    .orElseThrow(() -> ApiException.badRequest("Category not found."));
        }
        String currency = request.currency() == null || request.currency().isBlank()
                ? user.getBaseCurrency() : request.currency().toUpperCase(Locale.ROOT);
        recurring.setAccount(account);
        recurring.setCategory(category);
        recurring.setType(request.type());
        recurring.setAmount(request.amount());
        recurring.setCurrency(currency);
        recurring.setExchangeRate(currencyService.rate(currency, user.getBaseCurrency()));
        recurring.setDescription(request.description().trim());
        recurring.setFrequency(request.frequency());
        recurring.setNextRunDate(request.nextRunDate());
    }

    private RecurringTransaction findOwned(UUID userId, UUID id) {
        return recurringRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Recurring transaction not found."));
    }

    private RecurringResponse toResponse(RecurringTransaction r) {
        return new RecurringResponse(r.getId(), r.getAccount().getId(), r.getAccount().getName(),
                r.getCategory() == null ? null : r.getCategory().getId(),
                r.getCategory() == null ? "Uncategorized" : r.getCategory().getName(),
                r.getType(), r.getAmount(), r.getCurrency(), r.getDescription(), r.getFrequency(),
                r.getNextRunDate(), r.isActive());
    }
}
