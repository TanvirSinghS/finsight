package com.finsight.api.dto;

import com.finsight.api.domain.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TransactionDtos {
    private TransactionDtos() {
    }

    public record TransactionRequest(
            @NotNull UUID accountId,
            UUID categoryId,
            @NotNull TransactionType type,
            @NotNull @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
            @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotBlank @Size(max = 255) String description,
            @Size(max = 120) String merchant,
            @Size(max = 2000) String notes,
            @NotNull @PastOrPresent(message = "Date cannot be in the future") LocalDate occurredOn) {
    }

    public record TransactionResponse(
            UUID id, TransactionType type, BigDecimal amount, String currency, BigDecimal exchangeRate,
            BigDecimal amountBase, String description, String merchant, String notes, LocalDate occurredOn,
            UUID accountId, String accountName, UUID categoryId, String categoryName, String categoryColor,
            UUID recurringId) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    public record CsvImportResult(int imported, int skipped, List<String> errors) {
    }
}
