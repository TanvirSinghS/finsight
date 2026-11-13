package com.finsight.api.service;

import com.finsight.api.domain.*;
import com.finsight.api.dto.AccountDtos.AccountRequest;
import com.finsight.api.dto.AccountDtos.AccountResponse;
import com.finsight.api.exception.ApiException;
import com.finsight.api.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final UserService userService;
    private final CurrencyService currencyService;

    public AccountService(AccountRepository accountRepository, UserService userService,
                          CurrencyService currencyService) {
        this.accountRepository = accountRepository;
        this.userService = userService;
        this.currencyService = currencyService;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(UUID userId) {
        User user = userService.get(userId);
        return accountRepository.findByUserIdAndArchivedFalseOrderByName(userId).stream()
                .map(account -> toResponse(account, user))
                .toList();
    }

    @Transactional
    public AccountResponse create(UUID userId, AccountRequest request) {
        User user = userService.get(userId);
        String name = request.name().trim();
        if (accountRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw ApiException.conflict("An account named '" + name + "' already exists.");
        }
        Account account = new Account(user, name, request.type(),
                normalizeCurrency(request.currency(), user.getBaseCurrency()),
                request.initialBalance() == null ? BigDecimal.ZERO : request.initialBalance());
        return toResponse(accountRepository.save(account), user);
    }

    @Transactional
    public AccountResponse update(UUID userId, UUID accountId, AccountRequest request) {
        User user = userService.get(userId);
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> ApiException.notFound("Account not found."));
        String name = request.name().trim();
        if (!account.getName().equalsIgnoreCase(name)
                && accountRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw ApiException.conflict("An account named '" + name + "' already exists.");
        }
        account.setName(name);
        account.setType(request.type());
        account.setCurrency(normalizeCurrency(request.currency(), user.getBaseCurrency()));
        if (request.initialBalance() != null) {
            account.setInitialBalance(request.initialBalance());
        }
        accountRepository.save(account);
        return toResponse(account, user);
    }

    @Transactional
    public void delete(UUID userId, UUID accountId) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> ApiException.notFound("Account not found."));
        if (accountRepository.findByUserIdAndArchivedFalseOrderByName(userId).size() <= 1) {
            throw ApiException.badRequest("Keep at least one active account.");
        }
        account.setArchived(true);
        accountRepository.save(account);
    }

    private String normalizeCurrency(String currency, String fallback) {
        return currency == null || currency.isBlank() ? fallback : currency.toUpperCase(Locale.ROOT);
    }

    private AccountResponse toResponse(Account account, User user) {
        BigDecimal net = accountRepository.netForAccount(account.getId(), TransactionType.INCOME);
        BigDecimal opening = currencyService.convert(account.getInitialBalance(), account.getCurrency(),
                user.getBaseCurrency());
        BigDecimal balance = opening.add(net).setScale(2, RoundingMode.HALF_UP);
        return new AccountResponse(account.getId(), account.getName(), account.getType(), account.getCurrency(),
                account.getInitialBalance(), balance, account.isArchived());
    }
}
