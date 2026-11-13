package com.finsight.api.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class GoalDtos {
    private GoalDtos() {
    }

    public record GoalRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin("1.00") @Digits(integer = 12, fraction = 2) BigDecimal targetAmount,
            @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal currentAmount,
            LocalDate targetDate,
            @Pattern(regexp = "^#([0-9a-fA-F]{6})$") String color) {
    }

    public record ContributionRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount) {
    }

    public record GoalResponse(UUID id, String name, BigDecimal targetAmount, BigDecimal currentAmount,
                               BigDecimal remaining, int percentComplete, LocalDate targetDate, String color,
                               boolean completed) {
    }
}
