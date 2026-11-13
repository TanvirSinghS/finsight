package com.finsight.api.dto;

import com.finsight.api.domain.Frequency;
import com.finsight.api.domain.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class RecurringDtos {
    private RecurringDtos() {
    }

    public record RecurringRequest(
            @NotNull UUID accountId,
            UUID categoryId,
            @NotNull TransactionType type,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotBlank @Size(max = 255) String description,
            @NotNull Frequency frequency,
            @NotNull LocalDate nextRunDate,
            Boolean active) {
    }

    public record RecurringResponse(UUID id, UUID accountId, String accountName, UUID categoryId,
                                    String categoryName, TransactionType type, BigDecimal amount, String currency,
                                    String description, Frequency frequency, LocalDate nextRunDate, boolean active) {
    }
}
