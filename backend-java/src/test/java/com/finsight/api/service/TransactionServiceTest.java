package com.finsight.api.service;

import com.finsight.api.domain.*;
import com.finsight.api.dto.TransactionDtos.TransactionRequest;
import com.finsight.api.dto.TransactionDtos.TransactionResponse;
import com.finsight.api.exception.ApiException;
import com.finsight.api.repository.AccountRepository;
import com.finsight.api.repository.CategoryRepository;
import com.finsight.api.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private UserService userService;
    @Mock private CurrencyService currencyService;

    private TransactionService transactionService;
    private User user;
    private Account account;
    private Category category;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(transactionRepository, accountRepository,
                categoryRepository, userService, currencyService);
        user = new User("alex@example.com", "hash", "Alex", "USD");
        account = new Account(user, "Checking", AccountType.CHECKING, "USD", BigDecimal.ZERO);
        category = new Category(user, "Groceries", CategoryKind.EXPENSE, "#22c55e", "cart");
        when(userService.get(any())).thenReturn(user);
    }

    @Test
    void createConvertsForeignCurrencyToBaseAmount() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category));
        when(currencyService.rate("EUR", "USD")).thenReturn(new BigDecimal("1.10"));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.create(userId, new TransactionRequest(
                accountId, categoryId, TransactionType.EXPENSE, new BigDecimal("20.00"),
                "EUR", "Dinner", "Bistro", null, LocalDate.now().minusDays(1)));

        assertThat(response.amountBase()).isEqualByComparingTo("22.00");
        assertThat(response.exchangeRate()).isEqualByComparingTo("1.10");
        assertThat(response.currency()).isEqualTo("EUR");
        assertThat(response.categoryName()).isEqualTo("Groceries");
    }

    @Test
    void createRejectsCategoryWithWrongKind() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID incomeId = UUID.randomUUID();
        Category income = new Category(user, "Salary", CategoryKind.INCOME, "#059669", "banknote");
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));
        when(categoryRepository.findByIdAndUserId(incomeId, userId)).thenReturn(Optional.of(income));

        assertThatThrownBy(() -> transactionService.create(userId, new TransactionRequest(
                accountId, incomeId, TransactionType.EXPENSE, new BigDecimal("10.00"),
                "USD", "Mistake", null, null, LocalDate.now())))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRejectsForeignAccount() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.create(userId, new TransactionRequest(
                accountId, null, TransactionType.EXPENSE, new BigDecimal("10.00"),
                "USD", "Nope", null, null, LocalDate.now())))
                .isInstanceOf(ApiException.class);
    }
}
